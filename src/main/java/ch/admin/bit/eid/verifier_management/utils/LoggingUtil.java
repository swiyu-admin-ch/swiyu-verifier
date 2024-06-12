package ch.admin.bit.eid.verifier_management.utils;

import ch.admin.bit.eid.verifier_management.enums.LogEntryOperation;
import ch.admin.bit.eid.verifier_management.enums.LogEntryStatus;
import ch.admin.bit.eid.verifier_management.enums.LogEntryStep;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class LoggingUtil {

    public static String createLoggingMessage(String message,
                                              LogEntryStatus status,
                                              LogEntryOperation operation,
                                              LogEntryStep step,
                                              UUID managementId) {

        return String.format("%s. status=%s, operation=%s, step=%s, managementId=%s", message, status.name(), operation.name(), step.name(), managementId);
    }

    public static String createLoggingMessage(String message,
                                              LogEntryStatus status,
                                              LogEntryOperation operation) {

        return String.format("%s. status=%s, operation=%s", message, status.name(), operation.name());
    }
}
