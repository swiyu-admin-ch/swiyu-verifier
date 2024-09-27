package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.statuslist.StatusListReference;
import ch.admin.bit.eid.oid4vp.model.statuslist.StatusListReferenceFactory;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    protected void checkPresentationDefinitionCriteria(String credential) throws VerificationException {
        List<String> pathList = getPathToRequestedFields(managementEntity.getRequestedPresentation().getInputDescriptors(), "$");

        if (pathList.isEmpty()) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Validation criteria not matched, check the structure and values of the token", managementEntity);
        }

        try {
            ReadContext ctx = JsonPath.parse(credential);
            pathList.forEach(ctx::read);
        } catch (PathNotFoundException e) {
            throw VerificationException.credentialError(e, ResponseErrorCodeEnum.CREDENTIAL_INVALID, e.getMessage(), managementEntity);
        }
    }

    private String concatPaths(String parentPath, String path) {
        return parentPath + path.replace("$", "");
    }
}
