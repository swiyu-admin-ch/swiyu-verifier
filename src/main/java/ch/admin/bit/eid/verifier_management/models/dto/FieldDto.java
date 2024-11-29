package ch.admin.bit.eid.verifier_management.models.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Schema(name = "Field")
@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class FieldDto implements Serializable {

    @NotEmpty
    @Schema(description = "(Mandatory) Array of one or more JSONPath string expressions")
    // TODO NullOrJsonPath
    private List<String> path;

    @Schema(description = "(Optional) If present value MUST be a string that is unique")
    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    @Schema(description = "(Optional) If present describes purpose for which the field is requested")
    private String purpose;

    @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format")
    private FilterDto filter;

    // TODO other fields are currently ignored -> check https://identity.foundation/presentation-exchange/spec/v2.0.0/#input-descriptor
}
