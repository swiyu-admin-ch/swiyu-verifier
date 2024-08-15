package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.config.BBSKeyConfiguration;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.eid.bbscryptosuite.BbsCryptoSuite;
import ch.admin.eid.bbscryptosuite.CryptoSuiteVerificationResult;
import com.google.gson.Gson;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.GsonJsonParser;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
public class LdpCredential extends CredentialBuilder {

    private final BBSKeyConfiguration bbsKeyConfiguration;

    LdpCredential(BBSKeyConfiguration bbsKeyConfiguration) {
        this.bbsKeyConfiguration = bbsKeyConfiguration;
    }

    @Override
    public ManagementEntity verifyPresentation() {
        // TODO - Parse presentationSubmission and find out that we need to process it as BBS, ECDSA VC-DI or SD-JWT or JWT

        var walletResponseBuilder = ResponseData.builder();
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(vpToken);

        // TODO - Validate the integrity, authenticity, and Holder Binding of any Verifiable Presentation provided in the VP Token according to the rules of the respective Presentation format
        String verifiedDocument;
        try {
            verifiedDocument = verifyProofBBS(vpToken, managementEntity.getRequestNonce());
        } catch (Exception e) {
            log.error(e.toString());
            var errorCode = ResponseErrorCodeEnum.CREDENTIAL_INVALID;
            walletResponseBuilder.errorCode(errorCode);
            updateManagementObject(VerificationStatusEnum.FAILED, walletResponseBuilder.build());
            throw VerificationException.credentialError(errorCode, "The credential data integrity signature could not be verified");
        }

        // Confirm that the returned Credential(s) meet all criteria sent in the Presentation Definition in the Authorization Request.
        checkPresentationDefinitionCriteria(document, managementEntity);

        // TODO - Perform the checks required by the Verifier's policy based on the set of trust requirements such as trust frameworks it belongs to (i.e., revocation checks), if applicable.
        var parser = new GsonJsonParser();
        var credentialSubject = parser.parseMap(verifiedDocument).get("credentialSubject");
        walletResponseBuilder.credentialSubjectData(new Gson().toJson(credentialSubject));
        updateManagementObject(VerificationStatusEnum.SUCCESS, walletResponseBuilder.build());

        return managementEntity;
    }

    private void checkPresentationDefinitionCriteria(Object document, ManagementEntity management) throws VerificationException {

        boolean isValid = false;

        try {
            List<String> pathList = getAbsolutePaths(management.getRequestedPresentation().getInputDescriptors(), "$");

            if (!pathList.isEmpty()) {
                isValid = pathList.stream().allMatch(path -> isNotBlank(JsonPath.read(document, path)));
            }
        } catch (PathNotFoundException pathNotFoundException) {
            updateManagementObject(VerificationStatusEnum.FAILED, ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, pathNotFoundException.getMessage());
        }

        if (!isValid) {
            updateManagementObject(VerificationStatusEnum.FAILED, ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Validation criteria not matched, check the structure and values of the token");
        }
    }

    private String verifyProofBBS(String bbsCredential, String nonce) throws VerificationException {

        CryptoSuiteVerificationResult verificationResult;

        try (BbsCryptoSuite suite = new BbsCryptoSuite(bbsKeyConfiguration.getBBSKey())) {
            verificationResult = suite.verifyProof(bbsCredential, nonce, bbsKeyConfiguration.getPublicBBSKey());
        }

        if (!verificationResult.component1()) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Verification error");
        }

        return verificationResult.getVerifiedDocument();
    }
}
