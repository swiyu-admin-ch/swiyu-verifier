package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static java.util.Objects.nonNull;


/**
 * Default REST exception handler. Handles exceptions that are same for any controller.
 */
@RestControllerAdvice
@AllArgsConstructor
@Slf4j
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            // Handle invalid property exceptions during controller method invocation
            InvalidPropertyException.class,
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        return createBadRequestResponse(e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e, HttpServletRequest r) {
        if (e instanceof HttpRequestMethodNotSupportedException) {
            return createBadRequestResponse(e);
        }
        log.error("Unhandled exception occured for uri {}", r.getRequestURL(), e);
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @NotNull
    private static ResponseEntity<Object> createBadRequestResponse(Exception e) {
        String responseMessage = nonNull(e.getMessage()) ? e.getMessage() : "Bad request";
        log.debug("invalid request", e);
        return new ResponseEntity<>(responseMessage, HttpStatus.BAD_REQUEST);
    }
}
