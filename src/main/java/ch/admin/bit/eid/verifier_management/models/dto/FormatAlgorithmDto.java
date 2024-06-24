package ch.admin.bit.eid.verifier_management.models.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
public class FormatAlgorithmDto {

    @Schema(description = "(Optional) algorithm string from the JW* family")
    private List<String> alg;

    @Schema(description = "(Optional) Linked-Data integrity proof types")
    private List<String> proof_type;
}
