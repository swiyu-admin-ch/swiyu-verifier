package ch.admin.bj.swiyu.verifier.api.management.dcql;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Represents a Credential Set Query within a DCQL query.
 * A Credential Set Query is an object representing a request for one or more Credentials to satisfy
 * a particular use case with the Verifier.
 *
 * @see <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.2">OpenID for Verifiable Presentations 1.0, 6.2. Credential Set Query</a>
 */

@Schema(description = "Represents a Credential Set Query within a DCQL query according to " +
        "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.2")
public record DcqlCredentialSetDto(

        @Schema(description = "A non-empty array, where each value in the array is a list of Credential Query " +
                "identifiers representing one set of Credentials that satisfies the use case. " +
                "According to OpenID for Verifiable Presentations 1.0, 6.2 Credential Set Query, property 'options'.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("options")
        @NotEmpty(message = "options must not be empty")
        List<List<String>> options, // REQUIRED

        @Schema(description = "A boolean indicating if this credential set is required. (default is true)" +
                "According to OpenID for Verifiable Presentations 1.0, 6.2. Credential Set Query, property 'required'.",
                defaultValue = "true")
        @JsonProperty("required")
        Boolean required // OPTIONAL, default true
) {
}
