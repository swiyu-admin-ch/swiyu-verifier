package ch.admin.bj.swiyu.verifier.dto.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.Map;

/**
 * Transparency metadata required for a
 * Verification Query Public Statement (vqPS) at the Trust Management System (TMS).
 *
 * <p>When present in a {@link CreateVerificationManagementDto}, the verifier
 * automatically registers the DCQL query with the TMS using the provided
 * scope and localized display strings. The resulting signed vqPS JWT is
 * cached in the database and injected into subsequent Authorization Requests
 * to enable the wallet to display the Trust Protocol 2.0 "Transparent Verification Trust Mark".</p>
 */
@Builder
@Schema(name = "VerificationPurpose")
public record VerificationPurposeDto(

        @Valid
        @NotBlank
        @Schema(
                description = "Unique scope identifier for this specific query, e.g. 'com.example.age_verification'",
                example = "com.example.age_verification"
        )
        @JsonProperty("scope")
        String scope,

        @Valid
        @NotEmpty
        @Schema(
                description = "Localized purpose names (max 50 chars per value). Key is language tag (e.g. 'de-ch').",
                example = "{\"de-ch\": \"Altersverifikation\"}"
        )
        @JsonProperty("purpose_name")
        Map<String, String> purposeName,

        @Valid
        @NotEmpty
        @Schema(
                description = "Localized purpose descriptions (max 500 chars per value). Key is language tag (e.g. 'de-ch')."
        )
        @JsonProperty("purpose_description")
        Map<String, String> purposeDescription
) {
}

