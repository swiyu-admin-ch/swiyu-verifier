package ch.admin.bj.swiyu.verifier.api.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

public record OpenIdClientMetadataVpFormatsSupported(
        @Nullable
        @JsonProperty("dc+sd-jwt")
        OpenIdClientMetadataVpFormatSdJwt sdJwt
) {

}
