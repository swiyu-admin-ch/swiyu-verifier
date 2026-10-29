package ch.admin.bj.swiyu.verifier.dto.management;

import ch.admin.bj.swiyu.verifier.dto.VerificationErrorResponseCodeDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Schema(name = "ResponseData")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record ResponseDataDto(

        @JsonProperty("error_code")
        VerificationErrorResponseCodeDto errorCode,

        @JsonProperty("error_description")
        String errorDescription,

        @JsonProperty("credential_subject_data")
        @Schema(description = "Requested Claims, where the key is the id of the credential request. "
                + "Only present if `application.additional-audit-information.credential-subject-data-enabled` "
                + "is set to true (default: true).", deprecated = true)
        @Deprecated(forRemoval = true)
        Map<String, Object> credentialSubjectData,

        @JsonProperty("vp_token")
        @Schema(description = "Full presentation as sent by the wallet. "
                + "Only present if `application.additional-audit-information.vp-token-enabled` "
                + "is set to true (default: false).")
        Map<String, List<String>> vpToken,

        @JsonProperty("dcql_response")
        @Schema(description = """
                Values requested by the DCQL query as a series of maps.
                First map represents the dcql credential query where in the key is the id of the credential query.
                Second map represents the claims requested of that credential. The key for the claims is the id, or if not provided the path.
                Only present if `application.additional-audit-information.credential-subject-data-enabled` is set to true.
                (default: true)
                """, example = """
                    {
                        "my_hitchhiker_request": {
                            "name": ["Arthur Dent"],
                            "address.street_address": ["42 Market Street"],
                            "education": ["Bachelor of Science", "Master of Science"]
                        }
                    }
                """)
        Map<String, Map<String, List<Object>>> dcqlResponse
) {
}
