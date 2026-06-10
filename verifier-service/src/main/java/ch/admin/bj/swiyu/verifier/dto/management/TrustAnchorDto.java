package ch.admin.bj.swiyu.verifier.dto.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;

@Schema(name = "TrustAnchor")
public record TrustAnchorDto(
        @NotBlank
        @JsonProperty("did")
        @Schema(example = "did:example:12345")
        String did,

        @NotBlank
        @JsonProperty("trust_registry_uri")
        @Schema(description = """
                URI of the trust registry to be used for finding the trust anchor.
                For the example value the trust anchor would be searched at
                https://trust-reg.trust-infra.swiyu-int.admin.ch/api/v1/truststatements/{trust_anchor_did}
                """, example = "https://trust-reg.trust-infra.swiyu-int.admin.ch")
        @Pattern(regexp = "^https://.*", message = "Trust Registry URL must utilize https")
        @URL
        String trustRegistryUri
) {
}
