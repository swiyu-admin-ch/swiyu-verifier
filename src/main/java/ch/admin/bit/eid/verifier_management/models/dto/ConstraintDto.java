package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.models.validations.NullOrValues;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class ConstraintDto implements Serializable {

    @NotNull
    @NotEmpty
    private List<@Valid FieldDto> fields;

    @NullOrValues(values = {"required", "preferred"}, message = "must be null or preferred or required")
    @JsonProperty("limit_disclosure")
    private String limitDisclosure;
}
