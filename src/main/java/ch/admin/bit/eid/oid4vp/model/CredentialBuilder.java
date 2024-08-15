package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

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

    protected void updateManagementOnError(ResponseErrorCodeEnum errorCode) {
        updateManagementObject(VerificationStatusEnum.FAILED, ResponseData.builder().errorCode(errorCode).build());
    }

    private String concatPaths(String parentPath, String path) {
        return parentPath + path.replace("$", "");
    }
}
