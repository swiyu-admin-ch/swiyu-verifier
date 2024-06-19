package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.models.dto.ConstraintDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.Id;

import java.io.Serializable;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputDescriptor implements Serializable {

    /**
     * The Input Descriptor Object MAY contain a purpose property. If present, its value MUST be a string that describes the purpose for which the Claim's data is being requested.
     * The Input Descriptor Object MAY contain a format property. If present, its value MUST be an object with one or more properties matching the registered Claim Format Designations (e.g., jwt, jwt_vc, jwt_vp, etc.). This format property is identical in value signature to the top-level format object, but can be used to specifically constrain submission of a single input to a subset of formats or algorithms.
     */

    @Id
    @NotBlank
    @Schema(description = "(Mandatory) unique string with no conflict with another id in the Presentation Definition")
    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    // TODO should be defined
    private HashMap<String, Object> format;

    // The Input Descriptor Object MUST contain a constraints property. Its value MUST be an object composed as follows, unless otherwise specified by a Feature:
    @NotNull
    private List<ConstraintDto> constraints;
}
