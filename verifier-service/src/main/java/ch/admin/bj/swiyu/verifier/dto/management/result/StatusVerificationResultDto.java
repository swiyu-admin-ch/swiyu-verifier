package ch.admin.bj.swiyu.verifier.dto.management.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(name = "StatusVerificationResult")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record StatusVerificationResultDto(
        @Schema(description = "Whether the status of the credential is considered valid")
        boolean valid,
        @Schema(description = "Status value read from the Status List Token, e.g. 0 (VALID), 1 (INVALID), 2 (SUSPENDED)")
        Integer status,
        @JsonProperty("status_list_token")
        @Schema(description = "The Status List Token used to evaluate the status, exactly as fetched from the status "
                + "registry (compact JWS serialization), so that its signature can be verified later on for audit "
                + "purposes. Only present if ADDITIONAL_AUDIT_INFORMATION_STATUS_LIST_TOKEN_ENABLED was enabled when "
                + "the verification was completed.")
        String statusListToken) {
}
