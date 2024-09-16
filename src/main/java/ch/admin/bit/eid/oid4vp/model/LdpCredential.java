package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.config.BBSKeyConfig;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.did.DidJwk;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import ch.admin.eid.bbscryptosuite.BbsCryptoSuite;
import ch.admin.eid.bbscryptosuite.CryptoSuiteVerificationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class LdpCredential extends CredentialVerifier {

    private final BBSKeyConfig bbsKeyConfiguration;

    LdpCredential(final String vpToken,
                  final ManagementEntity managementEntity,
                  final PresentationSubmission presentationSubmission,
                  final VerificationManagementRepository verificationManagementRepository,
                  BBSKeyConfig bbsKeyConfiguration) {
        super(vpToken, managementEntity, presentationSubmission, verificationManagementRepository);
        this.bbsKeyConfiguration = bbsKeyConfiguration;
    }

    @Override
    public void verifyPresentation() {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> parsed;
        try {
            parsed = mapper.readValue(vpToken, HashMap.class);
        } catch (JsonProcessingException e) {
            throw credentialInvalidError(e, "vpToken can not be parsed as JSON");
        }
        // Unpack bbsToken from wrapper
        String bbsToken;
        try {
            bbsToken = mapper.writeValueAsString(((List) parsed.get("verifiableCredential")).get(0));
        } catch (JsonProcessingException e) {
            throw credentialInvalidError(e, "Credential could not be extracted from Presentation");
        }
        // We got no holder binding. If the VC has though a credential subject did we expect there to be a holder binding
        try {
            String holderBinding = JsonPath.read(bbsToken, "$.credentialSubject.id");
            // We found a credentialSubject id for a holder binding.
            verifyHolderBinding(parsed, holderBinding);

        } catch (PathNotFoundException e) {
            // All good, no holder did found
        }

        String verifiedDocument = verifyBBSSignature(bbsToken);
        // Confirm that the returned Credential(s) meet all criteria sent in the Presentation Definition in the Authorization Request.
        checkPresentationDefinitionCriteria(bbsToken);
        // TODO - Perform the checks required by the Verifier's policy based on the set of trust requirements such as trust frameworks it belongs to (i.e., revocation checks), if applicable.
        managementEntity.setState(VerificationStatusEnum.SUCCESS);
        managementEntity.setWalletResponse(ResponseData.builder().credentialSubjectData(verifiedDocument).build());
        verificationManagementRepository.save(managementEntity);
    }

    private VerificationException credentialInvalidError(Throwable cause, String description) {
        return VerificationException.credentialError(cause, ResponseErrorCodeEnum.CREDENTIAL_INVALID, description, managementEntity);
    }

    private VerificationException holderBindingInvalidError(String description) {
        return VerificationException.credentialError(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH, description, managementEntity);
    }

    private VerificationException holderBindingInvalidError(Throwable cause, String description) {
        return VerificationException.credentialError(cause, ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH, description, managementEntity);
    }

    private void verifyHolderBinding(Map<String, Object> parsed, String holderDid) {
        // Verify the holder binding and update the vp token
        var proof = parsed.get("proof");
        if (!(proof instanceof Map)) {
            throw holderBindingInvalidError("Holder Binding Proof not readable");
        }
        Map<String, Object> holderBindingProof = (Map<String, Object>) proof;
        if (!"EcdsaSignature2024".equals(holderBindingProof.get("type"))) {
            throw holderBindingInvalidError("Holder Binding must be of type EcdsaSignature2024");
        }
        if (!"authentication".equals(holderBindingProof.get("proofPurpose"))) {
            throw holderBindingInvalidError("proofPurpose must be authentication");
        }
        // Check if the proof is done by the correct holder

        if (!holderDid.equals(holderBindingProof.get("verificationMethod"))) {
            throw holderBindingInvalidError("Holder Binding miss match between proof and presented VC");
        }
        var did = new DidJwk(holderDid);
        if (!did.isValid()) {
            throw holderBindingInvalidError(String.format("%s is not a valid DID", did.getDid()));
        }

        JWSObject jws;
        try {
            jws = JWSObject.parse(holderBindingProof.get("proofValue").toString());
        } catch (ParseException e) {
            throw holderBindingInvalidError("proofValue is not a valid JWS");
        }
        try {
            ECDSAVerifier verifier = new ECDSAVerifier(did.toJWK().toECKey());
            if (!jws.verify(verifier)) {
                throw holderBindingInvalidError("Holder Binding proof invalid");
            }
            if (!managementEntity.getRequestNonce().equals(jws.getPayload().toString())) {
                throw holderBindingInvalidError("Incorrect nonce in the holder binding");
            }
        } catch (ParseException e) {
            throw holderBindingInvalidError(e, "DID could not be parsed to a valid JWK");
        } catch (JOSEException e) {
            throw holderBindingInvalidError(e, "Only EC Keys are supported");
        }
    }

    @NotNull
    private String verifyBBSSignature(String bbsToken) {
        String verifiedDocument;
        try {
            verifiedDocument = verifyProofBBS(bbsToken, managementEntity.getRequestNonce());
        } catch (Exception e) {
            log.warn("Failed BBS proof verification for token", e);
            throw credentialInvalidError(e, "The BBS credential data integrity signature could not be verified");
        }
        return verifiedDocument;
    }

    private String verifyProofBBS(String bbsCredential, String nonce) throws VerificationException {

        CryptoSuiteVerificationResult verificationResult;

        try (BbsCryptoSuite suite = new BbsCryptoSuite(bbsKeyConfiguration.getBBSKey())) {
            verificationResult = suite.verifyProof(bbsCredential, nonce, bbsKeyConfiguration.getPublicBBSKey());
        }

        if (!verificationResult.component1()) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Verification error", managementEntity);
        }

        return verificationResult.getVerifiedDocument();
    }
}
