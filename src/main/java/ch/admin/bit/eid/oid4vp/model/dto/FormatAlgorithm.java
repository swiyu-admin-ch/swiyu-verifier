package ch.admin.bit.eid.oid4vp.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "(Optional) algorithm string from the JW* family")
    private List<String> alg;

    @JsonProperty("proof_type")
    @Schema(description = "(Optional) Linked-Data integrity proof types")
    private List<String> proofType;
}
