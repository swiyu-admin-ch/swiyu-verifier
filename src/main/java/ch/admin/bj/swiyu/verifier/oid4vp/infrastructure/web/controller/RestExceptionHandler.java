package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static java.util.Objects.nonNull;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;


/**
 * Default REST exception handler. Handles exceptions that are same for any controller.
 */
@RestControllerAdvice
@AllArgsConstructor
@Slf4j
public class RestExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        String responseMessage = nonNull(e.getMessage()) ? e.getMessage() : "Bad request";
        log.debug("invalid request", e);
        return new ResponseEntity<>(responseMessage, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(INTERNAL_SERVER_ERROR)
    public void handleException(Exception e) {
        log.error("Unhandled exception occured", e);
    }
}
