package ch.admin.bit.eid.verifier_management.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FormatAlgorithmDto implements Serializable {

    @Schema(description = "(Optional) algorithm string from the JW* family")
    private List<String> alg;

    @JsonProperty("proof_type")
    @Schema(description = "(Optional) Linked-Data integrity proof types")
    private List<String> proofType;
}
