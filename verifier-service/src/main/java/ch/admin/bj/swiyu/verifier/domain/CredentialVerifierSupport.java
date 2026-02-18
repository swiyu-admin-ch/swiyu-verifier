package ch.admin.bj.swiyu.verifier.domain;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;
import static ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier.CREDENTIAL_FORMAT;
import static ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier.CREDENTIAL_FORMAT_NEW;
import static ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition.Field;
import static ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition.FormatAlgorithm;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Various utility methods to support the credential verification process.
 */
@UtilityClass
public class CredentialVerifierSupport {

    private static void checkField(Field field, String credentialPath, ReadContext ctx) throws VerificationException {
        var filter = field.filter();
        // If filter is set
        if (filter != null && !field.path().isEmpty()) {
            // If we have a filter we only want to have vct filter (business limitation for the time being)
            boolean hasAdditionalFilters = field.path().size() > 1 || !"$.vct".equals(field.path().getFirst());
            // VCT filtering is done by doing a string comparison with the const descriptor. For this it must be a String and have a value.
            boolean incorrectConstDescriptor = isNull(filter.constDescriptor()) || filter.constDescriptor().isEmpty() || !"string".equals(filter.type());
            if (hasAdditionalFilters || incorrectConstDescriptor) {
                // The Credential is not invalid, but we do not support the presentation sent by the wallet.
                throw credentialError(
                        PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED,
                        "Fields with filter constraint must only occur on path '$.vct' and in combination with the 'const' operation"
                );
            }
            String value = ctx.read(concatPaths(credentialPath, field.path().getFirst()));
            if (!filter.constDescriptor().equals(value)) {
                throw credentialError(
                        PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED,
                        "Validation criteria not matched, expected filter with const value '%s'".formatted(filter.constDescriptor())
                );
            }
        } else {
            // If filter is not set
            field.path().forEach(path -> {
                try {
                    ctx.read(concatPaths(credentialPath, path));
                } catch (PathNotFoundException e) {
                    throw credentialError(e, e.getMessage());
                }
            });
        }
    }

    public static void checkCommonPresentationDefinitionCriteria(String credential, Management managementEntity) throws VerificationException {
        ReadContext ctx = JsonPath.parse(credential);
        managementEntity
                .getRequestedPresentation()
                .inputDescriptors()
                .forEach(descriptor -> descriptor
                        .constraints()
                        .fields()
                        .forEach(field -> checkField(field, "$", ctx))
                );
    }

    public static FormatAlgorithm getRequestedFormat(String credentialFormat, Management managementEntity) {

        var presentationDefinition = managementEntity.getRequestedPresentation();
        final Map<String, FormatAlgorithm> formats = new HashMap<>();

        addFormatsToMap(presentationDefinition.format(), formats);
        presentationDefinition.inputDescriptors().forEach(descriptor -> addFormatsToMap(descriptor.format(), formats));
        // EIDOMNI-284 - Contract to only accept correct dc+sd-jwt
        var updatedFormats = formats.entrySet().stream().collect(
                Collectors.toMap(
                        e -> (e.getKey().replace(CREDENTIAL_FORMAT_NEW, CREDENTIAL_FORMAT)),
                        Map.Entry::getValue));
        return updatedFormats.get(credentialFormat.replace(CREDENTIAL_FORMAT_NEW, CREDENTIAL_FORMAT));
    }

    private static void addFormatsToMap(Map<String, FormatAlgorithm> inputFormats, Map<String, FormatAlgorithm> outputFormats) {
        if (nonNull(inputFormats) && !inputFormats.isEmpty()) {
            outputFormats.putAll(inputFormats);
        }
    }

    private static String concatPaths(String parentPath, String path) {
        return parentPath + path.replace("$", "");
    }
}
