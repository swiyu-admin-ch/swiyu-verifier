package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.models.validations.NullOrJsonPath;
import ch.admin.bit.eid.verifier_management.models.validations.NullOrValues;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConstraintDto {

    @NotNull
    private List<@Valid FieldDto> fields;

    @NullOrValues(values = {"required", "preferred"}, message = "must be null or preferred or required")
    @JsonProperty("limit_disclosure")
    private String limitDisclosure;

    // TODO: The fields object MAY contain a filter property, and if present its value MUST be a JSON Schema descriptor used to filter against the values returned from evaluation of the JSONPath string expressions in the path array.
    @NullOrJsonPath
    private String filter;

    // TODO: The fields object MAY contain an optional property. The value of this property MUST be a boolean, wherein true indicates the field is optional, and false or non-presence of the property indicates the field is required. Even when the optional property is present, the value located at the indicated path of the field MUST validate against the JSON Schema filter, if a filter is present.
    private boolean optional;
}
