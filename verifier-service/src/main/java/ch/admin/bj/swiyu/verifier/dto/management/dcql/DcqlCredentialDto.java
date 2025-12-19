package ch.admin.bj.swiyu.verifier.dto.management.dcql;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Represents a Credential Query within a DCQL query.
 * A Credential Query is an object representing a request for a presentation of one or more matching Credentials.
 *
 * @see <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.1">OpenID for Verifiable Presentations 1.0, Section 6.1</a>
 */
@Schema(description = "Represents a Credential Query within a DCQL query according to " +
        "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.1")
public record DcqlCredentialDto(

        @Schema(description = "A string identifying the Credential in the response and, if provided, the " +
                "constraints in credential_sets. The value MUST be a non-empty string consisting of " +
                "alphanumeric, underscore (_) or hyphen (-) characters. Within the Authorization Request, " +
                "the same id MUST NOT be present more than once. " +
                "According to OpenID for Verifiable Presentations 1.0, Section 6.1, property 'id'.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("id")
        @NotEmpty(message = "id must not be empty")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "id must contain only alphanumeric, underscore, or hyphen characters")
        String id,  // REQUIRED

        @Schema(description = "A string that specifies the format of the requested Credential. " +
                "Valid Credential Format Identifier values are defined in Appendix B. " +
                "According to OpenID for Verifiable Presentations 1.0, Section 6.1, property 'format'.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("format")
        @NotEmpty(message = "format must not be empty")
        String format, // REQUIRED

        @Schema(description = "A boolean which indicates whether multiple Credentials can be returned for " +
                "this Credential Query. If omitted, the default value is false. " +
                "According to OpenID for Verifiable Presentations 1.0, Section 6.1, property 'multiple'.",
                defaultValue = "false")
        @JsonProperty("multiple")
        Boolean multiple, // OPTIONAL, default false

        @Schema(description = "An object defining additional properties requested by the Verifier that apply to " +
                "the metadata and validity data of the Credential. The properties of this object are defined " +
                "per Credential Format. " +
                "According to OpenID for Verifiable Presentations 1.0, Section 6.1, property 'meta'.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("meta")
        @NotNull(message = "meta is required")
        @Valid
        DcqlCredentialMetaDto meta, // REQUIRED

        @Schema(description = "An optional non-empty array of Claims Query objects, specifying individual claims. " +
                "According to OpenID for Verifiable Presentations 1.0, Section 6.1, property 'claims'.")
        @JsonProperty("claims")
        @Valid
        @Size(min = 1, message = "claims must not be empty when provided")
        List<DcqlClaimDto> claims,  // OPTIONAL

        @Schema(description = "An optional non-empty array containing arrays of identifiers for elements in claims " +
                "that specifies which combinations of claims for the Credential are requested. " +
                "According to OpenID for Verifiable Presentations 1.0, Section 6.1, property 'claim_sets'.")
        @JsonProperty("claim_sets")
        @Valid
        @Size(min = 1, message = "claim_sets must not be empty when provided")
        List<List<String>> claimSets, // OPTIONAL

        @Schema(description = "A boolean indicating if cryptographic holder binding is required. If true, " +
                "the Wallet MUST return a Verifiable Presentation of a Verifiable Credential. If false, " +
                "a Verifiable Credential without Holder Binding MAY be returned. " +
                "If omitted, the default is to require cryptographic holder binding. " +
                "According to OpenID for Verifiable Presentations 1.0, Section 6.1, property 'require_cryptographic_holder_binding'. " +
                "Also referenced in Appendix B.1. " +
                "See <a href=\"https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.1\">Section 6.1</a> and " +
                "<a href=\"https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-w3c-verifiable-credentials\">Appendix B.1</a>.")
        @JsonProperty("require_cryptographic_holder_binding")
        Boolean requireCryptographicHolderBinding, // OPTIONAL

        @Schema(description = "An optional non-empty array of Trusted Authorities Query objects. " +
                "According to OpenID for Verifiable Presentations 1.0, Section 6.1, property 'trusted_authorities'.")
        @JsonProperty("trusted_authorities")
        List<DcqlTrustedAuthoritiesDto> trustedAuthorities // OPTIONAL
) {
}
