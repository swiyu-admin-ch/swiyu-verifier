package ch.admin.bit.eid.verifier_management.exceptions;

import ch.admin.bit.eid.verifier_management.enums.LogEntryOperation;
import ch.admin.bit.eid.verifier_management.enums.LogEntryStatus;
import ch.admin.bit.eid.verifier_management.enums.LogEntryStep;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

import static ch.admin.bit.eid.verifier_management.utils.LoggingUtil.createLoggingMessage;
import static org.springframework.http.HttpStatus.*;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(VerificationNotFoundException.class)
    protected ResponseEntity<Object> handleVerificationNotFoundException(final VerificationNotFoundException exception) {
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
    protected ResponseEntity<Object> handleGeneralException(final Exception exception) {
        final ApiError apiError = new ApiError(INTERNAL_SERVER_ERROR);
        apiError.setDetail("Internal Server Error. Please check again later");

        log.error(createLoggingMessage(exception.getMessage(),
                LogEntryStatus.ERROR,
                LogEntryOperation.VERIFICATION));

        return new ResponseEntity<>(apiError, apiError.getStatus());
    }

    /**
     * Override default MethodArgumentNotValidException handling to provide a more detailed error message.
     * Triggered when an object fails @Valid validation.
     * Shows the exact fields and the reason for the validation failure.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {

        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("%s: %s", error.getField(), error.getDefaultMessage()))
                .sorted()
                .collect(Collectors.joining(", "));

        final ApiError apiError = new ApiError(BAD_REQUEST);
        apiError.setDetail(errors);

        log.info(createLoggingMessage(String.format("%s: %s", BAD_REQUEST.getReasonPhrase(), errors),
                LogEntryStatus.ERROR,
                LogEntryOperation.VERIFICATION));

        return new ResponseEntity<>(apiError, apiError.getStatus());
    }
}
