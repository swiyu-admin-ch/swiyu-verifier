package ch.admin.bj.swiyu.verifier.api.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

import java.util.List;

public record OpenIdClientMetadataVpFormatSdJwt(
        @Nullable
        @JsonProperty("sd-jwt_alg_values")
        List<String> sdJwtAlgs,
        @Nullable
        @JsonProperty("kb-jwt_alg_values")
        List<String> kbJwtAlgs
) {
}
