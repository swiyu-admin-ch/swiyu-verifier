package ch.admin.bit.eid.verifier_management.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

@Getter
public class CreateManagementRequestDto {

    @Valid
    @NotEmpty
    @JsonProperty("input_descriptors")
    private List<InputDescriptorDto> inputDescriptors;
}
