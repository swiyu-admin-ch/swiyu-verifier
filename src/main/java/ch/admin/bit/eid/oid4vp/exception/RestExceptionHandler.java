package ch.admin.bit.eid.oid4vp.exception;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static java.util.Objects.nonNull;


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
}
