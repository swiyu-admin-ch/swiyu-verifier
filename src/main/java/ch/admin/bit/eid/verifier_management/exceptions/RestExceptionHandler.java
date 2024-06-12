package ch.admin.bit.eid.verifier_management.exceptions;

import ch.admin.bit.eid.verifier_management.enums.LogEntryOperation;
import ch.admin.bit.eid.verifier_management.enums.LogEntryStatus;
import ch.admin.bit.eid.verifier_management.enums.LogEntryStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static ch.admin.bit.eid.verifier_management.utils.LoggingUtil.createLoggingMessage;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(VerificationNotFoundException.class)
    protected ResponseEntity<Object> handleResourceNotFoundException(
            final VerificationNotFoundException exception, final WebRequest request
    ) {
        final ApiError apiError = new ApiError(NOT_FOUND);
        apiError.setDetail(exception.getMessage());

        log.info(createLoggingMessage("Verification not found",
                LogEntryStatus.ERROR,
                LogEntryOperation.VERIFICATION,
                LogEntryStep.VERIFICATION_RESPONSE,
                exception.getManagementId()));

        return new ResponseEntity<>(apiError, apiError.getStatus());
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleGeneralException(final Exception exception, final WebRequest request) {
        final ApiError apiError = new ApiError(INTERNAL_SERVER_ERROR);
        apiError.setDetail("Internal Server Error. Please check again later");

        log.info(createLoggingMessage("General verification error",
                LogEntryStatus.ERROR,
                LogEntryOperation.VERIFICATION));

        return new ResponseEntity<>(apiError, apiError.getStatus());
    }
}