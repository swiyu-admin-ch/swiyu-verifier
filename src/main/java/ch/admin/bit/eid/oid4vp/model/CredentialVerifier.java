package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.Field;
import ch.admin.bit.eid.oid4vp.model.dto.FormatAlgorithm;
import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import ch.admin.bit.eid.oid4vp.model.statuslist.StatusListReference;
import ch.admin.bit.eid.oid4vp.model.statuslist.StatusListReferenceFactory;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static java.util.Objects.nonNull;

@Getter
@Slf4j
public abstract class CredentialVerifier {

    protected ManagementEntity managementEntity;
    protected String vpToken;
    protected PresentationSubmission presentationSubmission;
    protected VerificationManagementRepository verificationManagementRepository;
    protected StatusListReferenceFactory statusListReferenceFactory;

    protected CredentialVerifier(final String vpToken,
                                 final ManagementEntity managementEntity,
                                 final PresentationSubmission presentationSubmission,
                                 final VerificationManagementRepository verificationManagementRepository,
                                 final StatusListReferenceFactory statusListReferenceFactory) {
        this.managementEntity = managementEntity;
        this.vpToken = vpToken;
        this.presentationSubmission = presentationSubmission;
        this.verificationManagementRepository = verificationManagementRepository;
        this.statusListReferenceFactory = statusListReferenceFactory;
    }

    public abstract void verifyPresentation();

    public void verifyStatus(Map<String, Object> vcClaims) {
        statusListReferenceFactory.createStatusListReferences(vcClaims, managementEntity).forEach(StatusListReference::verifyStatus);
    }

    protected List<String> getPathToRequestedFields(final List<InputDescriptor> inputDescriptorList, final String credentialPath) {
        List<String> pathList = new ArrayList<>();

        inputDescriptorList.forEach(descriptor -> descriptor.getConstraints().getFields()
                .forEach(field -> pathList.addAll(field.getPath().stream().map(str -> concatPaths(credentialPath, str)).toList())));

        return pathList;
    }

    private void checkField(Field field, String credentialPath, ReadContext ctx) throws VerificationException {
        var filter = field.getFilter();
        // If filter is set
        if (filter != null) {
            if(field.getPath().size() > 1 || !"const".equals(filter.getConstDescriptor())) throw VerificationException.credentialError(
                    ResponseErrorCodeEnum.CREDENTIAL_INVALID,
                    "Fields with filter constraint must only occur on path '$.vct' and in combination with the 'const' operation",
                    managementEntity
            );
            String value = ctx.read(concatPaths(credentialPath, field.getPath().getFirst()));
            if (!filter.getConstDescriptor().equals(value)) {
                throw VerificationException.credentialError(
                        ResponseErrorCodeEnum.CREDENTIAL_INVALID,
                        "Validation criteria not matched, expected filter with const value '%s'".formatted(filter.getConstDescriptor()), managementEntity
                );
            }
        } else {
            // If filter is not set
            field.getPath().forEach(path -> {
                try {
                    ctx.read(concatPaths(credentialPath, path));
                } catch (PathNotFoundException e) {
                    throw VerificationException.credentialError(e, ResponseErrorCodeEnum.CREDENTIAL_INVALID, e.getMessage(), managementEntity);
                }
            });
        }
    }

    protected void checkPresentationDefinitionCriteria(String credential) throws VerificationException {
        ReadContext ctx = JsonPath.parse(credential);
        managementEntity
                .getRequestedPresentation()
                .getInputDescriptors()
                .forEach(descriptor -> descriptor
                        .getConstraints()
                        .getFields()
                        .forEach(field -> checkField(field, "$", ctx))
                );
    }

    protected FormatAlgorithm getRequestedFormat(String credentialFormat) {

        PresentationDefinition presentationDefinition = this.managementEntity.getRequestedPresentation();
        Map<String, FormatAlgorithm> formats = new HashMap<>();

        addFormatsToMap(presentationDefinition.getFormat(), formats);
        presentationDefinition.getInputDescriptors().forEach(descriptor -> addFormatsToMap(descriptor.getFormat(), formats));

        return formats.get(credentialFormat);
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
