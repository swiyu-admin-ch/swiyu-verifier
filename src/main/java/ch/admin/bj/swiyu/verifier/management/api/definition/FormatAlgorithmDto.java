package ch.admin.bj.swiyu.verifier.management.api.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(name = "FormatAlgorithm")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormatAlgorithmDto {

    @NotNull
    @NotEmpty
    @JsonProperty("sd-jwt_alg_values")
    @Schema(description = "(Required) algorithms string from the SDJWT family")
    private List<String> alg;

    @NotNull
    @NotEmpty
    @JsonProperty("kb-jwt_alg_values")
    @Schema(description = "(Required) algorithms defining the keybinding algorithm for SDJWT family")
    private List<String> keyBindingAlg;
}
