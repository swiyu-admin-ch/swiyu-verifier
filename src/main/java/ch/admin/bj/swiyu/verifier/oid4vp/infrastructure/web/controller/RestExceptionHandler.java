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
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;

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

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Object> handleBrokenPipeException(IOException ex){
        if(ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
            // This is most likely a wrapped client abort exception meaning the client has already disconnected
            // Because there's no point in returning a response null is returned
            log.debug("Client aborted connection", ex);
            return null;
        }
        log.error("Unhandled IO exception occurred", ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Object> handleUnexpectedStreamClosing(MultipartException ex) {
        if(ex.getMessage() != null && ex.getMessage().contains("Stream ended unexpectedly")) {
            log.debug("Stream ended unexpectedly", ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
        log.error("Unhandled MultipartException exception occurred", ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
