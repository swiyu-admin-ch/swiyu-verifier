package ch.admin.bit.eid.oid4vp.exception;

import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static java.util.Objects.nonNull;


@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(VerificationException.class)
    protected ResponseEntity<Object> handleVerificationException(
            final VerificationException exception, final WebRequest request) {
        HttpStatus responseStatus = HttpStatus.BAD_REQUEST;
        if (exception.getError().getError().equals(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND)) {
            responseStatus = HttpStatus.NOT_FOUND;
        }
        return new ResponseEntity<>(exception.getError(), responseStatus);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<Object> handleIllegalArgumentException(
            final IllegalArgumentException exception, final WebRequest request) {
        HttpStatus responseStatus = HttpStatus.BAD_REQUEST;
        String responseMessage = nonNull(exception.getMessage()) ? exception.getMessage() : "Bad request";

        return new ResponseEntity<>(responseMessage, responseStatus);
    }

    /*@ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleGeneralException(
            final Exception exception, final WebRequest request){
        HttpStatus responseStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        String responseMessage = nonNull(exception.getMessage()) ? exception.getMessage() : "Internal server error. Please check again later";

        return new ResponseEntity<>(responseMessage, responseStatus);
    }*/
}