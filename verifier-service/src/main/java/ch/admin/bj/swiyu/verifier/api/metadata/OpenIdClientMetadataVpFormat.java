package ch.admin.bj.swiyu.verifier.api.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Defines formats and proof types that the Verifier supports.
 * From: <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html#name-additional-verifier-metadat">...</a>
 *
 * @param jwtVerifiablePresentation
 */
public record OpenIdClientMetadataVpFormat(
        @NotNull
        @JsonProperty("jwt_vp")
        @Valid
        OpenIdClientMetadataVpFormatAlg jwtVerifiablePresentation
) {
}