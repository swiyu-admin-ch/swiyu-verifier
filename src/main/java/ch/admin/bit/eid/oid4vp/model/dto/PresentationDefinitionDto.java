package ch.admin.bit.eid.oid4vp.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PresentationDefinitionDto {

    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    @Schema(description = "(Optional) Purpose for which the presentation definition is requested")
    private String purpose;

    @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format")
    private Map<String, FormatAlgorithm> format;

    @Valid
    @NotNull
    @JsonProperty("input_descriptors")
    private List<InputDescriptor> inputDescriptors;
}
