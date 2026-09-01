package ch.admin.bj.swiyu.verifier.dto.management;

import ch.admin.bj.swiyu.verifier.dto.VerificationErrorResponseCodeDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Schema(name = "ResponseData")
@Builder
public record ResponseDataDto(

        @JsonProperty("error_code")
        VerificationErrorResponseCodeDto errorCode,

        @JsonProperty("error_description")
        String errorDescription,

        @JsonProperty("credential_subject_data")
        @Schema(description = "Requested Claims, where the key is the id of the credential request")
        Map<String, Object> credentialSubjectData,

        @JsonProperty("vp_token")
        @Schema(description = "Full presentation as sent by the wallet, if audit feature has been activated")
        Map<String, List<String>> vpToken

) {
}
