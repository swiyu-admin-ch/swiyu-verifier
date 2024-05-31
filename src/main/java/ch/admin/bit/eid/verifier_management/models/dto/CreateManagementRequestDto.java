package ch.admin.bit.eid.verifier_management.models.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

@Getter
public class CreateManagementRequestDto {

    @NotEmpty
    private List<InputDescriptorDto> inputDescriptors;
}
