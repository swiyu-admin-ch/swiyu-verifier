package ch.admin.bit.eid.oid4vp.exception;

import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import lombok.Getter;

@Getter
public class VerificationException extends RuntimeException {
    private final VerificationError error;

    public VerificationException(VerificationError error) {
        this.error = error;
    }

    public static VerificationException submissionError(VerificationErrorEnum error) {
        return new VerificationException(
                new VerificationError(
                        error,
                        null, null
                )
        );
    }

    public static VerificationException credentialError(ResponseErrorCodeEnum errorCode,
                                                        String errorDescription) {
        return new VerificationException(
                new VerificationError(
                        VerificationErrorEnum.INVALID_REQUEST,
                        errorCode, errorDescription
                )
        );
    }
}
