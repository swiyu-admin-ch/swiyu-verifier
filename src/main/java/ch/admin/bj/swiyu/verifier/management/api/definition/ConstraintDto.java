package ch.admin.bj.swiyu.verifier.management.api.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Schema(name = "Constraint")
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class ConstraintDto {
    // todo clarify why it is non-null here, but not in oid4vp
    // @Id
    // @NotBlank(message = "Input descriptor is mandatory")
    @Schema(description = "(Mandatory) unique string with no conflict with another id in the Presentation Definition")
    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    @Schema(description = "(Optional) Purpose for which the data is requested")
    private String purpose;

    @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format")
    private Map<String, FormatAlgorithmDto> format;

    @NotNull
    @NotEmpty
    @Schema(description = "Selection which properties are requested of the holder", example = """
            [{"path": ["$.vct"],"filter":{"type": "string","const":"elfa-sdjwt"}},{"path":["$.dateOfBirth"]}]
            """)
    private List<@Valid FieldDto> fields;

    @NullOrValues(values = {"required", "preferred"}, message = "must be null or preferred or required")
    @JsonProperty("limit_disclosure")
    @Schema(description = "(Optional) If present has to be required or preferred.", example = "required")
    private String limitDisclosure;
}