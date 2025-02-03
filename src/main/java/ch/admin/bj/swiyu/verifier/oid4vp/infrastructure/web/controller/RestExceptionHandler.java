package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.controller;

import io.fabric8.kubernetes.client.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

import static java.util.Objects.nonNull;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;


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
        String responseMessage = nonNull(e.getMessage()) ? e.getMessage() : "Bad request";
        log.debug("invalid request", e);
        return new ResponseEntity<>(responseMessage, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Object> handleBrokenPipeException(IOException ex){
        if(ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
            // This is most likely a wrapped client abort exception meaning the client has already disconnected
            // Because there's no point in returning a response null is returned
            log.debug("Client aborted connection: {}", ex.getMessage());
            return null;
        }
        log.error("Unhandled IO exception occurred", ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(INTERNAL_SERVER_ERROR)
    public void handleException(Exception e, HttpServletRequest r) {
        log.error("Unhandled exception occured for uri {}", r.getRequestURL(), e);
    }
}
