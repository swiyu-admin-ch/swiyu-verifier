package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.config.BBSKeyConfiguration;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.Descriptor;
import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static ch.admin.bit.eid.oid4vp.utils.Base64Utils.decodeBase64;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
public class LdpCredential extends CredentialBuilder {

    private final BBSKeyConfiguration bbsKeyConfiguration;

    LdpCredential(BBSKeyConfiguration bbsKeyConfiguration) {
        this.bbsKeyConfiguration = bbsKeyConfiguration;
    }

    @Override
    public ManagementEntity verifyPresentation() {
        String decodedVpToken = decodeBase64(vpToken);

        // TODO - Parse presentationSubmission and find out that we need to process it as BBS, ECDSA VC-DI or SD-JWT or JWT

        var walletResponseBuilder = ResponseData.builder();
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(decodedVpToken);

        /*
         * Determine the number of VPs returned in the VP Token and identify in which VP which requested VC is included,
         * using the Input Descriptor Mapping Object(s) in the Presentation Submission.
         */
        String jsonpathToCredential = getPathToSupportedCredential(managementEntity, document, presentationSubmission);

        // TODO - Validate the integrity, authenticity, and Holder Binding of any Verifiable Presentation provided in the VP Token according to the rules of the respective Presentation format
        String verifiedDocument;
        try {
            verifiedDocument = verifyProofBBS(decodedVpToken, managementEntity.getRequestNonce());
        } catch (Exception e) {
            log.error(e.toString());
            String errorDescription = "The credential data integrity signature could not be verified";
            var errorCode = ResponseErrorCodeEnum.CREDENTIAL_INVALID;
            walletResponseBuilder.errorCode(errorCode);
            managementEntity.setWalletResponse(walletResponseBuilder.build());
            managementEntity.setState(VerificationStatusEnum.FAILED);
            updateManagementObjectAndThrowVerificationError(errorDescription, managementEntity);
            throw VerificationException.credentialError(errorCode, errorDescription); // TODO check why still needed
        }

        // Confirm that the returned Credential(s) meet all criteria sent in the Presentation Definition in the Authorization Request.
        checkPresentationDefinitionCriteria(document, jsonpathToCredential, managementEntity);

        // TODO - Perform the checks required by the Verifier's policy based on the set of trust requirements such as trust frameworks it belongs to (i.e., revocation checks), if applicable.

        managementEntity.setState(VerificationStatusEnum.SUCCESS);
        var parser = new GsonJsonParser();

        var credentialSubject = parser.parseMap(verifiedDocument).get("credentialSubject");
        walletResponseBuilder.credentialSubjectData(new Gson().toJson(credentialSubject));
        managementEntity.setWalletResponse(walletResponseBuilder.build());
        managementEntity.setState(VerificationStatusEnum.SUCCESS);
        return managementEntity;
    }

    protected String getPathToSupportedCredential(final ManagementEntity managementEntity,
                                                  final Object document,
                                                  final PresentationSubmission presentationSubmission) {

        if (isNull(document) || isNull(managementEntity) || isNull(presentationSubmission)) {
            throw new IllegalArgumentException("Document, management and presentation submission cannot be null");
        }

        List<Descriptor> descriptorMap = presentationSubmission.getDescriptorMap();

        boolean isCredentialList = JsonPath.read(document, "$") instanceof ArrayList<?>;

        if (isCredentialList) {
            Integer numberOfProvidedCreds = JsonPath.read(document, "$.length()");

            if (descriptorMap.size() != numberOfProvidedCreds) {
                var errorMessage = "Credential description does not match, credential";

                updateManagementObjectAndThrowVerificationError(errorMessage, managementEntity);
            }
        }

        List<String> supportedCredentialPaths = descriptorMap.stream()
                .map(descriptor -> getCredentialPath(descriptor, managementEntity, null))
                .filter(Objects::nonNull)
                .toList();

        if (supportedCredentialPaths.isEmpty()) {
            var errorMessage = "No matching paths with correct formats found";

            updateManagementObjectAndThrowVerificationError(errorMessage, managementEntity);
        }

        // TODO: assume only 1 credential at the moment
        return supportedCredentialPaths.getFirst();
    }

    protected void checkPresentationDefinitionCriteria(Object document,
                                                       String jsonPathToCredential,
                                                       ManagementEntity management) throws VerificationException {

        boolean isValid = false;

        if (jsonPathToCredential == null) {
            updateManagementObjectAndThrowVerificationError("Invalid credential path", management);
        }

        try {
            List<String> pathList = getAbsolutePaths(management.getRequestedPresentation().getInputDescriptors(), jsonPathToCredential);

            if (!pathList.isEmpty()) {
                isValid = pathList.stream().allMatch(path -> isNotBlank(JsonPath.read(document, path)));
            }
        } catch (PathNotFoundException pathNotFoundException) {
            // TODO check if nothing is revealed in logs || response
            updateManagementObjectAndThrowVerificationError(pathNotFoundException.getMessage(), management);
        }

        if (!isValid) {
            updateManagementObjectAndThrowVerificationError("Validation criteria not matched, check the structure and values of the token", management);
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

    private String getCredentialPath(Descriptor descriptor, ManagementEntity management, String currentPath) {

        if (isNull(descriptor) || isNull(management)) {
            throw new IllegalArgumentException("Vp token and descriptor cannot be null");
        }

        if (descriptor.getPathNested() != null) {
            getCredentialPath(descriptor.getPathNested(), management, descriptor.getPath());
        }

        String credFormat = descriptor.getFormat();
        Set<String> requestedFormats = getRequestedFormats(management);
        String path = currentPath != null ? concatPaths(currentPath, descriptor.getPath()) : descriptor.getPath();

        return requestedFormats.contains(credFormat.toLowerCase()) ? path : null;
    }

    private List<String> getAbsolutePaths(List<InputDescriptor> inputDescriptorList, String credentialPath) {
        List<String> pathList = new ArrayList<>();

        inputDescriptorList.forEach(descriptor -> descriptor.getConstraints()
                .forEach(constraints -> constraints.getFields()
                        .forEach(field -> pathList.addAll(field.getPath().stream().map(str -> concatPaths(credentialPath, str)).toList()))));

        return pathList;
    }

    private String concatPaths(String parentPath, String path) {
        return parentPath + path.replace("$", "");
    }
}
