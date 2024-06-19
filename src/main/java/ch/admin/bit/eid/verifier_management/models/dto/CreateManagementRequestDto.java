package ch.admin.bit.eid.verifier_management.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class CreateManagementRequestDto {

    @Valid
    @NotEmpty
    @JsonProperty("input_descriptors")
    private List<InputDescriptorDto> inputDescriptors;

    // private Map<String, Object> submissionRequirements;
}
