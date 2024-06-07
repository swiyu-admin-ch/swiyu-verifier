package ch.admin.bit.eid.verifier_management.models.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class CreateManagementRequestDto {

    @NotEmpty
    private List<InputDescriptorDto> inputDescriptors;

    private Map<String, Object> submissionRequirements;
}
