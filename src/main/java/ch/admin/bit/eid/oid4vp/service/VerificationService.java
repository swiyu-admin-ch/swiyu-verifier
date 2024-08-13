package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.config.BBSKeyConfiguration;
import ch.admin.bit.eid.oid4vp.model.PresentationFormatFactory;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public void processPresentation(ManagementEntity managementEntity, String vpToken, PresentationSubmission presentationSubmission) {

        var mimi = presentationFormatFactory
                .getFormatBuilder(presentationSubmission)
                .credentialOffer(vpToken, managementEntity, presentationSubmission, verificationManagementRepository)
                .verify();
    }
}
