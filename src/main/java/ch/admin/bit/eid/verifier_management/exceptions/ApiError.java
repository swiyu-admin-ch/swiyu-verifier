package ch.admin.bit.eid.verifier_management.exceptions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
public class ApiError {

    private HttpStatus status;

    private String detail;

    ApiError(HttpStatus status) {
        this.status = status;
        this.detail = status.getReasonPhrase();
    }
}
