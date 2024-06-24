package ch.admin.bit.eid.oid4vp.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class FormatAlgorithm {

    @Schema(description = "(Optional) algorithm string from the JW* family")
    private List<String> alg;

    @JsonProperty("proof_type")
    @Schema(description = "(Optional) Linked-Data integrity proof types")
    private List<String> proofType;
}
