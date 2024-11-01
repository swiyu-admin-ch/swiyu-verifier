package ch.admin.bit.eid.oid4vp.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormatAlgorithm {

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

    // TODO: remove in EID-1966
    @JsonProperty("proof_type")
    @Schema(description = "(Optional) Linked-Data integrity proof types")
    private List<String> proofType;
}
