/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.infrastructure.web;

import ch.admin.bj.swiyu.verifier.api.ApiErrorDto;
import ch.admin.bj.swiyu.verifier.api.VerificationErrorResponseDto;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static ch.admin.bj.swiyu.verifier.service.oid4vp.VerificationMapper.toVerficationErrorResponseDto;
import static java.util.Objects.nonNull;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

/**
 * Default REST exception handler. Handles exceptions that are same for any controller.
 */
@RestControllerAdvice
@AllArgsConstructor
@Slf4j
public class DefaultExceptionHandler extends ResponseEntityExceptionHandler {

    // TODO gapa: remove if not needed
//    @ExceptionHandler(Exception.class)
//    protected ResponseEntity<ApiErrorDto> handleGeneralException(final Exception exception, final HttpServletRequest request) {
//        log.error("Unkown exception for URL {}", request.getRequestURL(), exception);
//        return new ResponseEntity<>(new ApiErrorDto(INTERNAL_SERVER_ERROR, "Internal Server Error. Please check again later"), INTERNAL_SERVER_ERROR);
//    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e, HttpServletRequest r) {
        if (e instanceof HttpRequestMethodNotSupportedException) {
            return createBadRequestResponse(e);
        }
        log.error("Unhandled exception occured for uri {}", r.getRequestURL(), e);
        return new ResponseEntity<>(new ApiErrorDto(INTERNAL_SERVER_ERROR, "Internal Server Error. Please check again later"), INTERNAL_SERVER_ERROR);
    }

    /**
     * Override default MethodArgumentNotValidException handling to provide a more detailed error message.
     * Triggered when an object fails @Valid validation.
     * Shows the exact fields and the reason for the validation failure.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {

        var errors = ex.getBindingResult().getFieldErrors().stream().map(error -> String.format("%s: %s", error.getField(), error.getDefaultMessage())).sorted().collect(Collectors.joining(", "));

        log.info("Received bad request. Details: {}", errors);

        return new ResponseEntity<>(new ApiErrorDto(BAD_REQUEST, errors), BAD_REQUEST);
    }

    @ExceptionHandler({IllegalArgumentException.class,
            // Handle invalid property exceptions during controller method invocation
            InvalidPropertyException.class,})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        return createBadRequestResponse(e);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Object> handleBrokenPipeException(IOException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
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
        if (ex.getMessage() != null && ex.getMessage().contains("Stream ended unexpectedly")) {
            log.debug("Stream ended unexpectedly", ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
        log.error("Unhandled MultipartException exception occurred", ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> handleNoSuchElementException(NoSuchElementException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ProcessClosedException.class)
    public ResponseEntity<Object> handleProcessAlreadyClosedException(ProcessClosedException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.GONE);
    }

    @ExceptionHandler(VerificationException.class)
    ResponseEntity<VerificationErrorResponseDto> handleVerificationException(VerificationException e) {
        var error = toVerficationErrorResponseDto(e);
        log.warn("The received verification presentation could not be verified - caused by {}-{}:{}", error.error(), error.errorCode(), error.errorDescription(), e);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @NotNull
    private static ResponseEntity<Object> createBadRequestResponse(Exception e) {
        String responseMessage = nonNull(e.getMessage()) ? e.getMessage() : "Bad request";
        log.debug("invalid request", e);
        return new ResponseEntity<>(responseMessage, HttpStatus.BAD_REQUEST);
    }
}
