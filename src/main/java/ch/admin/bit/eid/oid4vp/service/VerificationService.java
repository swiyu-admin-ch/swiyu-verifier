package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.Descriptor;
import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.google.gson.Gson;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.boot.json.GsonJsonParser;
import org.springframework.stereotype.Service;
import uniffi.api.KeyPair;
import uniffi.cryptosuite.BbsCryptoSuite;
import uniffi.cryptosuite.CryptoSuiteVerificationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
@AllArgsConstructor
public class VerificationService {
    private final VerificationManagementRepository verificationManagementRepository;


    /**
     * Process the answer of a holder refusing to verify.
     *
     * @param managementEntity
     * @param error
     * @param errorDescription
     */
    public void processHolderVerificationRejection(ManagementEntity managementEntity, String error, String errorDescription) {
        // TODO check if reasons (error and errorDescription) needed
        ResponseData responseData = ResponseData
                .builder()
                .errorCode(ResponseErrorCodeEnum.CLIENT_REJECTED)
                .build();

        managementEntity.setWalletResponse(responseData);
        managementEntity.setState(VerificationStatusEnum.FAILED);

        verificationManagementRepository.save(managementEntity);
    }

    public void processPresentation(ManagementEntity managementEntity, String vpToken, PresentationSubmission presentationSubmission) {
        // TODO - Parse presentationSubmission and find out that we need to process it as BBS, ECDSA VC-DI or SD-JWT or JWT

        var walletResponseBuilder = ResponseData.builder();
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(vpToken);

        /*
         * Determine the number of VPs returned in the VP Token and identify in which VP which requested VC is included,
         * using the Input Descriptor Mapping Object(s) in the Presentation Submission.
         */
        String jsonpathToCredential = getPathToSupportedCredential(managementEntity, document, presentationSubmission);

        // TODO - Validate the integrity, authenticity, and Holder Binding of any Verifiable Presentation provided in the VP Token according to the rules of the respective Presentation format
        String verifiedDocument;
        try {
            verifiedDocument = verifyProofBBS(vpToken, managementEntity.getRequestNonce());
        } catch (Exception e) {
            String errorDescription = "The credential data integrity signature could not be verified";
            var errorCode = ResponseErrorCodeEnum.CREDENTIAL_INVALID;
            walletResponseBuilder.errorCode(errorCode);
            managementEntity.setWalletResponse(walletResponseBuilder.build());
            managementEntity.setState(VerificationStatusEnum.FAILED);
            verificationManagementRepository.save(managementEntity);
            throw VerificationException.credentialError(errorCode, errorDescription);
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
        verificationManagementRepository.save(managementEntity);
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

        // TODO check supported formats from input descriptor?
        if (supportedCredentialPaths.isEmpty()) {
            var errorMessage = "No supported credential format found";

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
            updateManagementObjectAndThrowVerificationError("Presentation definition error", management);
        }
    }

    private void updateManagementObjectAndThrowVerificationError(String errorMessage, ManagementEntity management) throws VerificationException {

        management.setWalletResponse(ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
        management.setState(VerificationStatusEnum.FAILED);
        verificationManagementRepository.save(management);

        throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, errorMessage);

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

    private Set<String> getRequestedFormats(ManagementEntity management) {

        Set<String> requestedFormats = new HashSet<>();

        management.getRequestedPresentation().getInputDescriptors().forEach(descriptor -> requestedFormats.addAll(descriptor.getFormat().keySet()));

        return requestedFormats;

    }

    private List<String> getAbsolutePaths(List<InputDescriptor> inputDescriptorList, String credentialPath) {
        List<String> pathList = new ArrayList<>();

        inputDescriptorList.forEach(descriptor -> descriptor.getConstraints()
                .forEach(constraints -> constraints.getFields()
                        .forEach(field -> pathList.addAll(field.getPath().stream().map(str -> concatPaths(credentialPath, str)).toList()))));

        return pathList;
    }

    private String verifyProofBBS(String bbsCredential, String nonce) throws VerificationException {
        // TODO Get the keys from VDR
        KeyPair keys = new KeyPair();
        CryptoSuiteVerificationResult verificationResult;

        try (BbsCryptoSuite suite = new BbsCryptoSuite(keys)) {
            verificationResult = suite.verifyProof(bbsCredential, nonce, keys.getPublicKey());
        }

        if (!verificationResult.component1()) {
            String errorDescription = "Verification error";
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, errorDescription);
        }

        return verificationResult.getVerifiedDocument();
    }

    private String concatPaths(String parentPath, String path) {
        return parentPath + path.replace("$", "");
    }
}
