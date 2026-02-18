package ch.admin.bj.swiyu.verifier.dto.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(name = "FormatAlgorithm")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FormatAlgorithmDto(
        @NotNull
        @NotEmpty
        @JsonProperty("sd-jwt_alg_values")
        @Schema(description = "(Required) algorithms string from the SDJWT family")
        List<String> alg,

        @NotNull
        @NotEmpty
        @JsonProperty("kb-jwt_alg_values")
        @Schema(description = "(Required) algorithms defining the keybinding algorithm for SDJWT family")
        List<String> keyBindingAlg
) {
}
