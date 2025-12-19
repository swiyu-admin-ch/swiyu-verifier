package ch.admin.bj.swiyu.verifier.dto.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OpenIdClientMetadataVpFormatAlg(
        @JsonProperty("alg")
        @NotEmpty
        @Valid
        @ValidJwtValues
        List<String> algorithms
) {
}