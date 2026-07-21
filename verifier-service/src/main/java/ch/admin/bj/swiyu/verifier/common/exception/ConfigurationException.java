package ch.admin.bj.swiyu.verifier.common.exception;

/**
 * Exception indicating that an error was made during the configuration phase of
 * the service
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

