package ch.admin.bj.swiyu.verifier.dto.management;

import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlQueryDto;
import ch.admin.bj.swiyu.verifier.dto.management.result.CredentialEvaluationDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(name = "ManagementResponse")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record ManagementResponseDto(
        UUID id,
        @JsonProperty("request_nonce")
        String requestNonce,
        VerificationStatusDto state,
        @JsonProperty("dcql_query")
        DcqlQueryDto dcqlQuery,
        @JsonProperty("credential_evaluation")
        @Schema(description = "Object of Evaluations, where the key is the id of the dcql_query credential")
        // Note: This is List<CredentialEvaluation> if multiple=true is used
        Map<String, List<CredentialEvaluationDto>> credentialEvaluation, 
        @JsonProperty("wallet_response")
        ResponseDataDto walletResponse,
        @JsonProperty("verification_url")
        String verificationUrl,
        @JsonProperty("verification_deeplink")
        String verificationDeeplink
) {
}