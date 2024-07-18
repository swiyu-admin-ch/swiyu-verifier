package ch.admin.bit.eid.oid4vp.exception;

import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationError {
    @NotNull
    private VerificationErrorEnum error;
    private ResponseErrorCodeEnum errorCode;
    private String errorDescription;
}
