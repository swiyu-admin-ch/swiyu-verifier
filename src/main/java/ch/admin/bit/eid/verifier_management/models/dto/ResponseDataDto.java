package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.enums.ResponseErrorCodeEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Builder
@Getter
@Setter
public class ResponseDataDto {

    private UUID id;

    @JsonProperty("error_code")
    private ResponseErrorCodeEnum errorCode;

    @JsonProperty("credential_subject_data")
    private Map<String, Object> credentialSubjectData;
}
