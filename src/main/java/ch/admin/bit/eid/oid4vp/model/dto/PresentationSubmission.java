package ch.admin.bit.eid.oid4vp.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PresentationSubmission {

    @NotBlank(message = "Presentation submission id is mandatory")
    private String id;

    @JsonProperty("definition_id")
    private String definitionId;

    @JsonProperty("descriptor_map")
    private List<Descriptor> descriptorMap;
}


