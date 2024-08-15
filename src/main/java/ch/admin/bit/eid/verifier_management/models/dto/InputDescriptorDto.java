package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.models.validations.NullOrFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputDescriptorDto implements Serializable {

    @Id
    @NotBlank(message = "Input descriptor is mandatory")
    @Schema(description = "(Mandatory) unique string with no conflict with another id in the Presentation Definition")
    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    @Schema(description = "(Optional) Purpose for which the data is requested")
    private String purpose;

    @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format")
    @NullOrFormat
    private Map<String, FormatAlgorithmDto> format;

    @NotNull
    @Valid
    private ConstraintDto constraints;
}
