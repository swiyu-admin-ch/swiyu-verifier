package ch.admin.bj.swiyu.verifier.oid4vp.common.exception;

/**
 * This exception is used when a process is trying to be accessed in a way which would lead to edits.
 */
public class ProcessClosedException extends RuntimeException {
    public ProcessClosedException() {
        super("Verification Process has already been closed.");
    }
}
