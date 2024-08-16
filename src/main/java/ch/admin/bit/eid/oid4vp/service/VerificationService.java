package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.config.BBSKeyConfiguration;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.PresentationFormatFactory;
import ch.admin.bit.eid.oid4vp.model.dto.Descriptor;
import ch.admin.bit.eid.oid4vp.model.dto.FormatAlgorithm;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ch.admin.bit.eid.oid4vp.utils.Base64Utils.decodeBase64;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@Service
@AllArgsConstructor
public class VerificationService {

    private final VerificationManagementRepository verificationManagementRepository;

    private final BBSKeyConfiguration bbsKeyConfiguration;

    private final PresentationFormatFactory presentationFormatFactory;

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

    public void processPresentation(ManagementEntity managementEntity, String request, PresentationSubmission presentationSubmission) {

        var credentialToBeProcessed = request;
        var isList = presentationSubmission.getDescriptorMap().size() > 1;
        // Todo consider more than 1 format
        var format = presentationSubmission.getDescriptorMap().getFirst().getFormat();

        // lists and ldp are base64urlencoded
        if (format == null || format.isEmpty()) {
            throw new IllegalArgumentException("No format " + format);
        }

        if (isList || format.contains("ldp")) {
            Object decodedToken = Configuration.defaultConfiguration().jsonProvider().parse(decodeBase64(request));

            String jsonpathToCredential = getPathToSupportedCredential(managementEntity, decodedToken, presentationSubmission);
            Object vpToken = JsonPath.read(decodedToken, jsonpathToCredential);

            if (format.contains("ldp")) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    credentialToBeProcessed = mapper.writeValueAsString(vpToken);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            } else {
                credentialToBeProcessed = (String) vpToken;
            }
        }

        presentationFormatFactory
                .getFormatBuilder(presentationSubmission)
                .credentialOffer(credentialToBeProcessed, managementEntity, presentationSubmission, verificationManagementRepository)
                .verify();
    }

    public String getPathToSupportedCredential(final ManagementEntity managementEntity,
                                               final Object document,
                                               final PresentationSubmission presentationSubmission) {

        if (isNull(document) || isNull(managementEntity) || isNull(presentationSubmission)) {
            throw new IllegalArgumentException("Document, management and presentation submission cannot be null");
        }

        List<Descriptor> descriptorMap = presentationSubmission.getDescriptorMap();

        boolean isCredentialList = JsonPath.read(document, "$") instanceof ArrayList<?>;

        // todo
        if (isCredentialList) {
            Integer numberOfProvidedCreds = JsonPath.read(document, "$.length()");

            if (descriptorMap.size() != numberOfProvidedCreds) {

                // updateManagementObject(VerificationStatusEnum.FAILED, ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
                throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Credential description does not match, credential");
            }
        }

        List<String> supportedCredentialPaths = descriptorMap.stream()
                .map(descriptor -> getCredentialPaths(descriptor, managementEntity))
                .filter(l -> !l.isEmpty())
                .findFirst()
                .orElse(null);

        if (supportedCredentialPaths == null || supportedCredentialPaths.isEmpty()) {
            // updateManagementObject(VerificationStatusEnum.FAILED, ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "No matching paths with correct formats found");
        }

        // TODO: assume only 1 credential at the moment
        return supportedCredentialPaths.getFirst();
    }

    private List<String> getCredentialPaths(Descriptor descriptor, ManagementEntity management) {

        List<String> paths = new ArrayList<>();

        if (isNull(descriptor)) {
            throw new IllegalArgumentException("Vp token and descriptor cannot be null");
        }

        if (descriptor.getPathNested() == null && checkIfProvidedFormatIsRequested(descriptor, management)) {
            paths.add(descriptor.getPath());
        } else if (descriptor.getPathNested() != null) {
            getNestedCredentialPaths(descriptor.getPathNested(), descriptor.getPath(), paths, management);
        }

        // Else Throw error

        return paths;
    }

    private List<String> getNestedCredentialPaths(Descriptor descriptor, String currentPath, List<String> paths, ManagementEntity management) {

        if (isNull(descriptor)) {
            throw new IllegalArgumentException("Vp token and descriptor cannot be null");
        }

        String path = currentPath != null ? concatPaths(currentPath, descriptor.getPath()) : descriptor.getPath();

        if (descriptor.getPathNested() == null && checkIfProvidedFormatIsRequested(descriptor, management)) {
            paths.add(path);
            return paths;
        } else if (descriptor.getPathNested() != null) {
            return getNestedCredentialPaths(descriptor.getPathNested(), descriptor.getPath(), paths, management);
        }

        return paths;
    }

    private boolean checkIfProvidedFormatIsRequested(Descriptor descriptor, ManagementEntity management) {
        String credFormat = descriptor.getFormat();
        Set<String> requestedFormats = getRequestedFormats(management);

        return requestedFormats.contains(credFormat.toLowerCase());
    }

    private String concatPaths(String parentPath, String path) {
        return parentPath + path.replace("$", "");
    }

    protected Set<String> getRequestedFormats(ManagementEntity management) {

        PresentationDefinition presentationDefinition = management.getRequestedPresentation();
        Map<String, FormatAlgorithm> formats = new HashMap<>();

        addFormatsToMap(presentationDefinition.getFormat(), formats);
        presentationDefinition.getInputDescriptors().forEach(descriptor -> addFormatsToMap(descriptor.getFormat(), formats));

        return formats.keySet();
    }

    private void addFormatsToMap(Map<String, FormatAlgorithm> inputFormats, Map<String, FormatAlgorithm> outputFormats) {
        if (nonNull(inputFormats) && !inputFormats.isEmpty()) {
            outputFormats.putAll(inputFormats);
        }
    }
}
