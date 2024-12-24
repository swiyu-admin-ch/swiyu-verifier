package ch.admin.bj.swiyu.verifier.management.infrastructure.web.controller;

import ch.admin.bj.swiyu.verifier.management.api.ApiErrorDto;
import jakarta.servlet.http.HttpServletRequest;
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

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiErrorDto> handleGeneralException(final Exception exception, final HttpServletRequest request) {
        log.error("Unkown exception for URL {}", request.getRequestURL(), exception);
        return new ResponseEntity<>(new ApiErrorDto(INTERNAL_SERVER_ERROR,
                "Internal Server Error. Please check again later"), INTERNAL_SERVER_ERROR);
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

        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("%s: %s", error.getField(), error.getDefaultMessage()))
                .sorted()
                .collect(Collectors.joining(", "));

        log.info("Received bad request. Details: {}", errors);

        return new ResponseEntity<>(new ApiErrorDto(BAD_REQUEST, errors), BAD_REQUEST);
    }
}
