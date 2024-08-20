package ch.admin.bit.eid.oid4vp.exception;

import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import lombok.Getter;

@Getter
public class VerificationException extends RuntimeException {
    private final transient VerificationError error;
    private final transient ManagementEntity managementEntity;

    public VerificationException(VerificationError error, ManagementEntity managementEntity) {
        this.error = error;
        this.managementEntity = managementEntity;
    }

    public static VerificationException submissionError(VerificationErrorEnum error, ManagementEntity managementEntity) {
        return new VerificationException(
                new VerificationError(
                        error,
                        null, null
                ),
                managementEntity
        );
    }

    public static VerificationException credentialError(ResponseErrorCodeEnum errorCode,
                                                        String errorDescription, ManagementEntity managementEntity) {
        return new VerificationException(
                new VerificationError(
                        VerificationErrorEnum.INVALID_REQUEST,
                        errorCode, errorDescription
                ), managementEntity
        );
    }
}
