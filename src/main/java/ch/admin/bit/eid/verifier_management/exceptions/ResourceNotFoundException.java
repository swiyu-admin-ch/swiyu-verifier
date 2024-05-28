package ch.admin.bit.eid.verifier_management.exceptions;

import java.text.MessageFormat;
import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

    private static final MessageFormat ERR_MESSAGE = new MessageFormat("Resource with id ''{0}'' not found");

    public ResourceNotFoundException(UUID id) {
        super(String.format("Resource with id '%S' not found", id.toString()));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
