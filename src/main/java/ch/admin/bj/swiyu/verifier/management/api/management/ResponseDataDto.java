package ch.admin.bj.swiyu.verifier.management.api.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Schema(name = "ResponseData")
@Builder
@Data
@AllArgsConstructor
public class ResponseDataDto {

    @JsonProperty("error_code")
    private VerificationErrorResponseCodeDto errorCode;

    @JsonProperty("error_description")
    private String errorDescription;

    @JsonProperty("credential_subject_data")
    private Map<String, Object> credentialSubjectData;
}
