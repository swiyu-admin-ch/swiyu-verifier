package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.models.InputDescriptor;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Getter
public class PresentationDefinitionDto {

    private UUID id;

    private List<InputDescriptor> inputDescriptors;

    private HashMap<String, Object> submissionRequirements;
}
