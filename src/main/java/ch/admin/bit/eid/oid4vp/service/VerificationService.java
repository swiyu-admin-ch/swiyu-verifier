package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class VerificationService {
    private final VerificationManagementRepository verificationManagementRepository;


    public void processErrorResponse(ManagementEntity managementEntity, String error, String errorDescription) {
        managementEntity.setWalletResponse(
                ResponseData.builder()
                        .errorCode(ResponseErrorCodeEnum.CLIENT_REJECTED)
                        .build()
        );
        managementEntity.setState(VerificationStatusEnum.FAILED);

        verificationManagementRepository.save(managementEntity);
    }

    public void processPresentation(ManagementEntity managementEntity, String vpToken, String presentationSubmission) {

    }
}
