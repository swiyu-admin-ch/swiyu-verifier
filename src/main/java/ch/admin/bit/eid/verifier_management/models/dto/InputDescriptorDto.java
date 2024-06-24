package ch.admin.bit.eid.verifier_management.models.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;

import java.util.Formattable;
import java.util.HashMap;
import java.util.List;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputDescriptorDto {

    @Id
    @NotBlank(message = "Input descriptor is mandatory")
    @Schema(description = "(Mandatory) unique string with no conflict with another id in the Presentation Definition")
    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    @Schema(description = "(Optional) Purpose for which the data is requested")
    private String purpose;

    // TODO check if format should also be set here in addition to presentation definition

    @NotNull
    private List<@Valid ConstraintDto> constraints;
}
