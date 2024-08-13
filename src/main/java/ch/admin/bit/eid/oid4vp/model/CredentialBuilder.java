package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.Descriptor;
import ch.admin.bit.eid.oid4vp.model.dto.FormatAlgorithm;
import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Getter
@Slf4j
public abstract class CredentialBuilder {

    protected ManagementEntity managementEntity;
    protected String vpToken;
    protected PresentationSubmission presentationSubmission;
    protected VerificationManagementRepository verificationManagementRepository;

    public CredentialBuilder credentialOffer(final String vpToken,
                                             final ManagementEntity managementEntity,
                                             final PresentationSubmission presentationSubmission,
                                             final VerificationManagementRepository verificationManagementRepository) {
        this.managementEntity = managementEntity;
        this.vpToken = vpToken;
        this.presentationSubmission = presentationSubmission;
        this.verificationManagementRepository = verificationManagementRepository;

        return this;
    }

    public abstract ManagementEntity verifyPresentation();

    public ManagementEntity verify() {
        return verifyPresentation();
    }

    protected Set<String> getRequestedFormats(ManagementEntity management) {

        PresentationDefinition presentationDefinition = management.getRequestedPresentation();
        Map<String, FormatAlgorithm> formats = new HashMap<>();

        addFormatsToMap(presentationDefinition.getFormat(), formats);
        presentationDefinition.getInputDescriptors().forEach(descriptor -> addFormatsToMap(descriptor.getFormat(), formats));

        return formats.keySet();
    }

    public String getPathToSupportedCredential(final ManagementEntity managementEntity,
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

                updateManagementObject(VerificationStatusEnum.FAILED, ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
                throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Credential description does not match, credential");
            }
        }

        List<String> supportedCredentialPaths = descriptorMap.stream()
                .map(descriptor -> getCredentialPaths(descriptor).getFirst())
                .filter(Objects::nonNull)
                .toList();

        if (supportedCredentialPaths.isEmpty()) {
            updateManagementObject(VerificationStatusEnum.FAILED, ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "No matching paths with correct formats found");
        }

        // TODO: assume only 1 credential at the moment
        return supportedCredentialPaths.getFirst();
    }

    public void checkPresentationDefinitionCriteria(Object document,
                                                    String jsonPathToCredential,
                                                    ManagementEntity management) throws VerificationException {

        boolean isValid = false;

        if (jsonPathToCredential == null) {
            updateManagementObject(VerificationStatusEnum.FAILED, ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Invalid credential path");
        }

        try {
            List<String> pathList = getAbsolutePaths(management.getRequestedPresentation().getInputDescriptors(), jsonPathToCredential);

            if (!pathList.isEmpty()) {
                isValid = pathList.stream().allMatch(path -> isNotBlank(JsonPath.read(document, path)));
            }
        } catch (PathNotFoundException pathNotFoundException) {
            // TODO check if nothing is revealed in logs || response
            updateManagementObject(VerificationStatusEnum.FAILED, ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, pathNotFoundException.getMessage());
        }

        if (!isValid) {
            updateManagementObject(VerificationStatusEnum.FAILED, ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Validation criteria not matched, check the structure and values of the token");
        }
    }

    private List<String> getCredentialPaths(Descriptor descriptor) {

        List<String> paths = new ArrayList<>();

        if (isNull(descriptor)) {
            throw new IllegalArgumentException("Vp token and descriptor cannot be null");
        }

        if (descriptor.getPathNested() == null && checkIfProvidedFormatIsRequested(descriptor)) {
            paths.add(descriptor.getPath());
        } else if (descriptor.getPathNested() != null) {
            getNestedCredentialPaths(descriptor.getPathNested(), descriptor.getPath(), paths);
        }

        // Else Throw error

        return paths;
    }

    private List<String> getNestedCredentialPaths(Descriptor descriptor, String currentPath, List<String> paths) {

        if (isNull(descriptor)) {
            throw new IllegalArgumentException("Vp token and descriptor cannot be null");
        }

        String path = currentPath != null ? concatPaths(currentPath, descriptor.getPath()) : descriptor.getPath();

        if (descriptor.getPathNested() == null && checkIfProvidedFormatIsRequested(descriptor)) {
            paths.add(path);
            return paths;
        } else if (descriptor.getPathNested() != null) {
            return getNestedCredentialPaths(descriptor.getPathNested(), descriptor.getPath(), paths);
        }

        return paths;
    }

    private boolean checkIfProvidedFormatIsRequested(Descriptor descriptor) {
        String credFormat = descriptor.getFormat();
        Set<String> requestedFormats = getRequestedFormats(this.managementEntity);

        return requestedFormats.contains(credFormat.toLowerCase());
    }

    protected List<String> getAbsolutePaths(List<InputDescriptor> inputDescriptorList, String credentialPath) {
        List<String> pathList = new ArrayList<>();

        inputDescriptorList.forEach(descriptor -> descriptor.getConstraints()
                .forEach(constraints -> constraints.getFields()
                        .forEach(field -> pathList.addAll(field.getPath().stream().map(str -> concatPaths(credentialPath, str)).toList()))));

        return pathList;
    }

    protected void updateManagementObject(VerificationStatusEnum status, ResponseData responseData) {
        managementEntity.setState(status);
        managementEntity.setWalletResponse(responseData);
        verificationManagementRepository.save(managementEntity);
    }

    private void addFormatsToMap(Map<String, FormatAlgorithm> inputFormats, Map<String, FormatAlgorithm> outputFormats) {
        if (nonNull(inputFormats) && !inputFormats.isEmpty()) {
            outputFormats.putAll(inputFormats);
        }
    }

    private String concatPaths(String parentPath, String path) {
        return parentPath + path.replace("$", "");
    }
}
