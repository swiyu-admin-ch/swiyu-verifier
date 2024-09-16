package ch.admin.bit.eid.oid4vp.exception;

import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import lombok.Getter;

@Getter
public class VerificationException extends RuntimeException {
    private final transient VerificationError error;
    private final transient ManagementEntity managementEntity;

    private VerificationException(Throwable cause, VerificationError error, ManagementEntity managementEntity) {
        super(cause);
        this.error = error;
        this.managementEntity = managementEntity;
    }

    public static VerificationException submissionError(VerificationErrorEnum error, ManagementEntity managementEntity) {
        return new VerificationException(
                null /* submissionError is only caused by business cases and not exceptions */,
                new VerificationError(
                        error,
                        null, null
                ),
                managementEntity
        );
    }

    public static VerificationException credentialError(ResponseErrorCodeEnum errorCode,
                                                        String errorDescription, ManagementEntity managementEntity) {
        return credentialError(
                null /* in case of manual throwing there is no cause */,
                errorCode,
                errorDescription,
                managementEntity);
    }

    public static VerificationException credentialError(Throwable cause, ResponseErrorCodeEnum errorCode,
                                                        String errorDescription, ManagementEntity managementEntity) {
        return new VerificationException(
                cause,
                new VerificationError(
                        VerificationErrorEnum.INVALID_REQUEST,
                        errorCode, errorDescription
                ), managementEntity
        );
    }
}
