package ch.admin.bit.eid.oid4vp.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Field {

    @NotEmpty
    @Schema(description = "(Mandatory) Array of one or more JSONPath string expressions")
    private List<String> path;

    @Schema(description = "(Optional) If present value MUST be a string that is unique")
    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    @Schema(description = "(Optional) If present describes purpose for which the field is requested")
    private String purpose;

// TODO other fields are currently ignored -> check https://identity.foundation/presentation-exchange/spec/v2.0.0/#input-descriptor
}
