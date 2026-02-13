package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.dto.submission.DescriptorDto;
import ch.admin.bj.swiyu.verifier.dto.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import lombok.experimental.UtilityClass;

import java.util.*;

import static ch.admin.bj.swiyu.verifier.common.util.base64.Base64Utils.decodeBase64;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Utility class to extract the verifiable credential from the VP token in case the submission was submitted
 * with multiple descriptors. Otherwise, it returns the VP token as is. Currently, the verifier supports only
 * a single credential to be verified.
 */
@UtilityClass
public class VerifiableCredentialExtractor {
    /**
     * Extracts the verifiable credential from the VP token for the given presentationSubmission. In case of multiple
     * descriptors in the submission, it returns the matching credential based on the requested formats in the
     * presentation definition.
     *
     * @return the matching verifiable credential from the VP token
     */
    public static String extractVerifiableCredential(String vpToken, Management managementEntity, PresentationSubmissionDto presentationSubmission) {
        var isList = presentationSubmission.getDescriptorMap().size() > 1;
        if (!isList) {
            return vpToken;
        }
        var decodedToken = Configuration.defaultConfiguration().jsonProvider().parse(decodeBase64(vpToken));
        var jsonpathToCredential = getPathToSupportedCredential(managementEntity, decodedToken, presentationSubmission);
        var credential = JsonPath.read(decodedToken, jsonpathToCredential);
        return (String) credential;
    }

    public static String getPathToSupportedCredential(final Management managementEntity,
                                                      final Object document,
                                                      final PresentationSubmissionDto presentationSubmission) {

        if (isNull(document) || isNull(managementEntity) || isNull(presentationSubmission)) {
            throw new IllegalArgumentException("Document, management and presentation submission cannot be null");
        }

        var descriptorMap = presentationSubmission.getDescriptorMap();
        var isCredentialList = JsonPath.read(document, "$") instanceof ArrayList<?>;

        if (isCredentialList) {
            Integer numberOfProvidedCreds = JsonPath.read(document, "$.length()");
            if (descriptorMap.size() != numberOfProvidedCreds) {
                throw credentialError( PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED,"Credential description does not match, credential");
            }
        }

        var supportedCredentialPaths = descriptorMap.stream()
                .map(descriptor -> getCredentialPaths(descriptor, managementEntity))
                .filter(l -> !l.isEmpty())
                .findFirst()
                .orElse(null);

        if (supportedCredentialPaths == null || supportedCredentialPaths.isEmpty()) {
            throw credentialError( PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED,"No matching paths with correct formats found");
        }

        // TODO: assume only 1 credential at the moment
        return supportedCredentialPaths.getFirst();
    }


    private static List<String> getCredentialPaths(DescriptorDto descriptor, Management management) {
        var paths = new ArrayList<String>();
        if (isNull(descriptor)) {
            throw new IllegalArgumentException("Vp token and descriptor cannot be null");
        }

        if (descriptor.getPathNested() == null && checkIfProvidedFormatIsRequested(descriptor, management)) {
            paths.add(descriptor.getPath());
        } else if (descriptor.getPathNested() != null) {
            getNestedCredentialPaths(descriptor.getPathNested(), descriptor.getPath(), paths, management);
        }

        return paths;
    }

    private static List<String> getNestedCredentialPaths(DescriptorDto descriptor, String currentPath, List<String> paths, Management management) {
        if (isNull(descriptor)) {
            throw new IllegalArgumentException("Vp token and descriptor cannot be null");
        }

        var path = currentPath != null ? concatPaths(currentPath, descriptor.getPath()) : descriptor.getPath();

        if (descriptor.getPathNested() == null && checkIfProvidedFormatIsRequested(descriptor, management)) {
            paths.add(path);
            return paths;
        } else if (descriptor.getPathNested() != null) {
            return getNestedCredentialPaths(descriptor.getPathNested(), descriptor.getPath(), paths, management);
        }

        return paths;
    }

    private static boolean checkIfProvidedFormatIsRequested(DescriptorDto descriptor, Management management) {
        var credFormat = descriptor.getFormat();
        var requestedFormats = getRequestedFormats(management);
        return requestedFormats.contains(credFormat.toLowerCase());
    }

    private static String concatPaths(String parentPath, String path) {
        return parentPath + path.replace("$", "");
    }

    private static Set<String> getRequestedFormats(Management management) {

        var presentationDefinition = management.getRequestedPresentation();
        var formats = new HashMap<String, PresentationDefinition.FormatAlgorithm>();

        addFormatsToMap(presentationDefinition.format(), formats);
        presentationDefinition.inputDescriptors().forEach(descriptor -> addFormatsToMap(descriptor.format(), formats));

        return formats.keySet();
    }


    private static void addFormatsToMap(Map<String, PresentationDefinition.FormatAlgorithm> inputFormats, Map<String, PresentationDefinition.FormatAlgorithm> outputFormats) {
        if (nonNull(inputFormats) && !inputFormats.isEmpty()) {
            outputFormats.putAll(inputFormats);
        }
    }
}
