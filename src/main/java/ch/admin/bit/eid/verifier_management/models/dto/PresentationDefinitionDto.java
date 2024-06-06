package ch.admin.bit.eid.verifier_management.models.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
public class PresentationDefinitionDto {

    private UUID id;

    private List<InputDescriptorDto> inputDescriptors;

    private Map<String, Object> submissionRequirements;
}
