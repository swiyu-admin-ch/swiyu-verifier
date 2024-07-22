package ch.admin.bit.eid.oid4vp.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;

import java.util.List;
import java.util.Map;

// https://identity.foundation/presentation-exchange/spec/v2.0.0/#input-descriptor-object
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Constraint {

    @Id
    @NotBlank(message = "Input descriptor is mandatory")
    @Schema(description = "(Mandatory) unique string with no conflict with another id in the Presentation Definition")
    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    @Schema(description = "(Optional) Purpose for which the data is requested")
    private String purpose;

    @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format")
    private Map<String, FormatAlgorithm> format;

    @NotNull
    private List<@Valid Field> fields;
}
