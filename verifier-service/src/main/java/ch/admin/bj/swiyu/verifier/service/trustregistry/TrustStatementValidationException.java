package ch.admin.bj.swiyu.verifier.service.trustregistry;

/**
 * Thrown when a Trust Protocol 2.0 trust statement JWT fails validation,
 * e.g. when the {@code kid} header is not anchored to the expected Trust Registry host.
 */
public class TrustStatementValidationException extends RuntimeException {

    /**
     * Constructs a new exception with the given message.
     *
     * @param message description of the validation failure
     */
    public TrustStatementValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the given message and cause.
     *
     * @param message description of the validation failure
     * @param cause   the underlying exception
     */
    public TrustStatementValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

