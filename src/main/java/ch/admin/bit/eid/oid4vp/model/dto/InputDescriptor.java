package ch.admin.bit.eid.oid4vp.model.dto;

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

import java.util.HashMap;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputDescriptor {

    /**
     * The Input Descriptor Object MUST contain an id property. The value of the id property MUST be a string that does not conflict with the id of another Input Descriptor Object in the same Presentation Definition and SHOULD not conflict with any other id value present in the same Presentation Definition.
     * The Input Descriptor Object MAY contain a name property. If present, its value SHOULD be a human-friendly name that describes what the target schema represents.
     * The Input Descriptor Object MAY contain a purpose property. If present, its value MUST be a string that describes the purpose for which the Claim's data is being requested.
     * The Input Descriptor Object MAY contain a format property. If present, its value MUST be an object with one or more properties matching the registered Claim Format Designations (e.g., jwt, jwt_vc, jwt_vp, etc.). This format property is identical in value signature to the top-level format object, but can be used to specifically constrain submission of a single input to a subset of formats or algorithms.
     */

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
    private List<@Valid Constraint> constraints;
}
