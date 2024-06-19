package ch.admin.bit.eid.oid4vp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class Descriptor {
    /**
     * The descriptor_map object MUST include an id property. The value of this property MUST be a string that matches the id property of the Input Descriptor in the Presentation Definition that this Presentation Submission is related to.
     * The descriptor_map object MUST include a format property. The value of this property MUST be a string that matches one of the Claim Format Designation. This denotes the data format of the Claim.
     * The descriptor_map object MUST include a path property. The value of this property MUST be a JSONPath string expression. The path property indicates the Claim submitted in relation to the identified Input Descriptor, when executed against the top-level of the object the Presentation Submission is embedded within.
     * The object MAY include a path_nested object to indicate the presence of a multi-Claim envelope format. This means the Claim indicated is to be decoded separately from its parent enclosure.
     */

    @NotNull
    private String id;

    @NotBlank
    private String format;

    // TODO must be json path
    @NotBlank
    private String path;

    @JsonProperty("path_nested")
    private Descriptor pathNested;
}
