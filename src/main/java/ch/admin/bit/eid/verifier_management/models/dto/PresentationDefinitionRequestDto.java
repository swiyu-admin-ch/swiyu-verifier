package ch.admin.bit.eid.verifier_management.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PresentationDefinitionRequestDto {

    @NotNull
    @JsonProperty("input_descriptors")
    private List<InputDescriptorDto> inputDescriptors;

    @JsonProperty("client_metadata")
    private ClientMetadataDto clientMetadata;
}
