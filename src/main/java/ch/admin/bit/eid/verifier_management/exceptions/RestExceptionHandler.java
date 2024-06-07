package ch.admin.bit.eid.verifier_management.exceptions;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(VerificationNotFoundException.class)
    protected ResponseEntity<Object> handleResourceNotFoundException(
            final VerificationNotFoundException exception, final WebRequest request
    ) {
        final ApiError apiError = new ApiError(NOT_FOUND);
        apiError.setDetail(exception.getMessage());

        // TODO add log

        return new ResponseEntity<>(apiError, apiError.getStatus());
    }
}