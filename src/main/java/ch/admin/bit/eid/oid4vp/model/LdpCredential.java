package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.config.BBSKeyConfiguration;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class LdpCredential extends CredentialVerifier {

    private final BBSKeyConfiguration bbsKeyConfiguration;

    LdpCredential(final String vpToken,
                  final ManagementEntity managementEntity,
                  final PresentationSubmission presentationSubmission,
                  final VerificationManagementRepository verificationManagementRepository,
                  BBSKeyConfiguration bbsKeyConfiguration) {
        super(vpToken, managementEntity, presentationSubmission, verificationManagementRepository);
        this.bbsKeyConfiguration = bbsKeyConfiguration;
    }

    private VerificationException credentialInvalidError(String description) {
        return VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, description, managementEntity);
    }

    private boolean isVerifiablePresentation(Object type) {
        if (type == null){
            throw credentialInvalidError("VP Token type must be set");
        }
        List<Object> tokenType = null;
        if (type instanceof List) {
            tokenType = (List<Object>) type;
        } else {
            tokenType = List.of(type);
        }
        return tokenType.contains("VerifiablePresentation");
    }

    @Override
    public void verifyPresentation() {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> parsed = null;
        try {
            parsed = mapper.readValue(vpToken, HashMap.class);
        } catch (JsonProcessingException e) {
            throw credentialInvalidError("vpToken can not be parsed as JSON");
        }
        String bbsToken = vpToken;
        if (isVerifiablePresentation(parsed.get("type"))) {
            // Verify the holder binding and update the vp token
            // TODO - Validate the integrity, authenticity, and Holder Binding of any Verifiable Presentation provided in the VP Token according to the rules of the respective Presentation format

            try {
                bbsToken = mapper.writeValueAsString(((List)parsed.get("verifiableCredential")).get(0));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }


        String verifiedDocument;
        try {
            verifiedDocument = verifyProofBBS(bbsToken, managementEntity.getRequestNonce());
        } catch (Exception e) {
            log.warn("Failed BBS proof verification for token", e);
            throw credentialInvalidError("The BBS credential data integrity signature could not be verified");
        }

        // Confirm that the returned Credential(s) meet all criteria sent in the Presentation Definition in the Authorization Request.
        checkPresentationDefinitionCriteria(vpToken);

        // TODO - Perform the checks required by the Verifier's policy based on the set of trust requirements such as trust frameworks it belongs to (i.e., revocation checks), if applicable.
        managementEntity.setState(VerificationStatusEnum.SUCCESS);
        managementEntity.setWalletResponse(ResponseData.builder().credentialSubjectData(verifiedDocument).build());
        verificationManagementRepository.save(managementEntity);
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
