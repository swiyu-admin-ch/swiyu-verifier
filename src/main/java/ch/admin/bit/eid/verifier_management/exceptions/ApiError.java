package ch.admin.bit.eid.verifier_management.exceptions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
public class ApiError {

    @JsonIgnore
    private HttpStatus status;

    private String detail;

    ApiError(HttpStatus status) {
        this.status = status;
        this.detail = status.getReasonPhrase();
    }

    ApiError(HttpStatus status, Throwable exception) {
        this.status = status;
        this.detail = exception.getMessage();
    }

    ApiError(HttpStatus status, String message) {
        this.status = status;
        this.detail = message;
    }
}
