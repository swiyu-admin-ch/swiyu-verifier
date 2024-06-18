package ch.admin.bit.eid.oid4vp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PresentationSubmissionDto {
/**
 * The presentation_submission object MUST be included at the top-level of an Embed Target, or in the specific location described in the Embed Locations table in the Embed Target section below.
 * The presentation_submission object MUST contain an id property. The value of this property MUST be a unique identifier, such as a UUID.
 * The presentation_submission object MUST contain a definition_id property. The value of this property MUST be the id value of a valid Presentation Definition.
 * The presentation_submission object MUST include a descriptor_map property. The value of this property MUST be an array of Input Descriptor Mapping Objects, composed as follows:
 */

    // must fields
    private UUID id;

    @JsonProperty("definition_id")
    private String definitionId;

    @JsonProperty("descriptor_map")
    private List<Descriptor> descriptorMap;
}


