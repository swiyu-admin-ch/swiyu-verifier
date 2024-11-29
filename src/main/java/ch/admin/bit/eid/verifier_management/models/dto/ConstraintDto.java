package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.models.validations.NullOrValues;
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

@Schema(name = "Constraint")
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class ConstraintDto {

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