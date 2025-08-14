package ch.admin.bj.swiyu.verifier.api.management.dcql;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Represents an individual claim object within the 'claims' array of a Credential Query.
 *
 * @see <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.3">OpenID for Verifiable Presentations 1.0, Section 6.3 (Claims Query)</a>
 */
@Schema(description = "Represents an individual claim object within the 'claims' array of a Credential Query according to " +
        "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.3")
public record DcqlClaimDto(


        @Schema(description = "REQUIRED if claim_sets is present in the Credential Query; OPTIONAL otherwise. A string " +
                "identifying the particular claim. The value MUST be a non-empty string consisting of alphanumeric, " +
                "underscore (_), or hyphen (-) characters. Within the particular claims array, the same id " +
                "MUST NOT be present more than once.")
        @JsonProperty("id")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "id must contain only alphanumeric, underscore, or hyphen characters")
        String id, // REQUIRED if claim_sets is present, OPTIONAL otherwise

        @Schema(description = "The path to the claim within the credential. " +
                "According to OpenID for Verifiable Presentations 1.0, Section 6.3, property 'path'." +
                "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#claims_path_pointer",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("path")
        @NotEmpty(message = "path must not be empty")
        List<String> path // REQUIRED

) {
}
