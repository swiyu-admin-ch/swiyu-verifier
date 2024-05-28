package ch.admin.bit.eid.verifier_management.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PresentationDefinitionRequestDto {

    @JsonProperty("input_descriptors")
    private List<InputDescriptorDto> inputDescriptors;

    @JsonProperty("client_metadata")
    private ClientMetadataDto clientMetadata;
}
