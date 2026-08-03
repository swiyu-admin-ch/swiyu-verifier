package ch.admin.bj.swiyu.verifier.dto.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenIdClientMetadataVpFormatsSupported(
        @JsonProperty("dc+sd-jwt")
        OpenIdClientMetadataVpFormatSdJwt sdJwt
) { }
