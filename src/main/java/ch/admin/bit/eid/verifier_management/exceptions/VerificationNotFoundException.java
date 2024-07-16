package ch.admin.bit.eid.verifier_management.exceptions;

import lombok.Getter;

import java.text.MessageFormat;
import java.util.UUID;

@Getter
public class VerificationNotFoundException extends RuntimeException {

    private static final MessageFormat ERR_MESSAGE = new MessageFormat("The verification with the identifier ''{0}'' was not found");

    private final UUID managementId;

    public VerificationNotFoundException(UUID id) {

        super(ERR_MESSAGE.format(new Object[]{id}));
        this.managementId = id;
    }
}
