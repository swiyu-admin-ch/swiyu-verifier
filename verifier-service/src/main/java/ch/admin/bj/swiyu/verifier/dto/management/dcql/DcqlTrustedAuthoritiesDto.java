package ch.admin.bj.swiyu.verifier.dto.management.dcql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Represents "Trusted Authorities Query" configuration in a DCQL query.
 * This DTO assists in identifying authorities or trust frameworks responsible for certifying issuers.
 * @see <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.1.1">6.1.1. Trusted Authorities Query</a>
 */
@Schema(description = "Trusted Authorities Query according to " +
        "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.1.1")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DcqlTrustedAuthoritiesDto(

        @Schema(description = "A string uniquely identifying the type of information about the issuer trust framework. " +
                "According to OpenID for Verifiable Presentations 1.0, Section 6.1.1, property 'type'.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("type")
        @NotEmpty(message = "type must not be empty")
        String type, // REQUIRED

        @Schema(description = "A non-empty array of strings, where each string (value) contains information " +
                "specific to the used Trusted Authorities Query type that allows the identification of " +
                "an issuer, a trust framework, or a federation that an issuer belongs to. " +
                "According to OpenID for Verifiable Presentations 1.0, Section 6.1.1, property 'values'.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("values")
        @NotEmpty(message = "values must not be empty")
        List<String> values // REQUIRED
) {
}
