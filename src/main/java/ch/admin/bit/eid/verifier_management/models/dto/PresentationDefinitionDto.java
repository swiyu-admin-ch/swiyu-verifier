package ch.admin.bit.eid.verifier_management.models.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PresentationDefinitionDto {

    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    @Schema(description = "(Optional) Purpose for which the presentation definition is requested")
    private String purpose;

    @Valid
    @NotNull
    @JsonProperty("input_descriptors")
    private List<InputDescriptorDto> inputDescriptors;
}
