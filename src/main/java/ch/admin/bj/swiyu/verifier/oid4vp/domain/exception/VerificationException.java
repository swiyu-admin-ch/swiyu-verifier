package ch.admin.bj.swiyu.verifier.oid4vp.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public class VerificationException extends RuntimeException {
    private final VerificationError errorType;
    private final VerificationErrorResponseCode errorResponseCode;
    private final String errorDescription;

    private VerificationException(Throwable cause, VerificationError errorType, VerificationErrorResponseCode errorResponseCode, String errorDescription) {
        super(cause);
        this.errorType = errorType;
        this.errorResponseCode = errorResponseCode;
        this.errorDescription = errorDescription;
    }

    public static VerificationException submissionError(VerificationError error) {
        return new VerificationException(
                null /* submissionError is only caused by business cases and not exceptions */,
                error,
                null,
                null
        );
    }


    public static VerificationException submissionError(VerificationError error, String errorDescription) {
        return new VerificationException(
                null /* submissionError is only caused by business cases and not exceptions */,
                error,
                null,
                errorDescription
        );
    }

    public static VerificationException credentialError(VerificationErrorResponseCode errorCode,
                                                        String errorDescription) {
        return credentialError(
                null /* in case of manual throwing there is no cause */,
                errorCode,
                errorDescription);
    }

    public static VerificationException credentialError(Throwable cause, VerificationErrorResponseCode errorCode,
                                                        String errorDescription) {
        return new VerificationException(
                cause,
                VerificationError.INVALID_REQUEST,
                errorCode,
                errorDescription
        );
    }

    /**
     * Get the http status code which should be used for this exception configuration.
     */
    public HttpStatusCode getSuggestedHttpStatus() {
        var httpStatus = HttpStatus.BAD_REQUEST;
        switch (this.getErrorType()) {
            case VERIFICATION_PROCESS_CLOSED -> httpStatus = HttpStatus.GONE;
            case AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND -> httpStatus = HttpStatus.NOT_FOUND;
        }
        return httpStatus;
    }
}
