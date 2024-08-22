package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.enums.ResponseErrorCodeEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class ResponseDataDto {

    @JsonProperty("error_code")
    private ResponseErrorCodeEnum errorCode;

    @JsonProperty("error_description")
    private String errorDescription;

    @JsonProperty("credential_subject_data")
    private Map<String, Object> credentialSubjectData;
}
