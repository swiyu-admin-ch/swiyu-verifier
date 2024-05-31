package ch.admin.bit.eid.verifier_management.exceptions;

import java.text.MessageFormat;
import java.util.UUID;

public class VerificationNotFinishedException extends RuntimeException  {

    private static final MessageFormat ERR_MESSAGE = new MessageFormat("The verification ''{0}'' has not reached a final state (error, success)");

    /*
    error = "verification_not_finished"
    error_description = "The verification has not reached a final state (error, success)"
     */
    public VerificationNotFinishedException(UUID id) {
        super(ERR_MESSAGE.format(new Object[]{id}));
    }
}
