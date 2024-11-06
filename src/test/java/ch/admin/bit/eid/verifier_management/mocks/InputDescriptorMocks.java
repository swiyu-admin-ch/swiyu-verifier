package ch.admin.bit.eid.verifier_management.mocks;

import ch.admin.bit.eid.verifier_management.models.dto.ConstraintDto;
import ch.admin.bit.eid.verifier_management.models.dto.InputDescriptorDto;

public class InputDescriptorMocks {

    public static InputDescriptorDto getInputDescriptorDto() {
        return InputDescriptorDto.builder()
                .id("id")
                .name("name")
                .purpose("purpose")
                .constraints(ConstraintDto.builder().build())
                .format(FormatAlgorithmMocks.createFormatAlgorithmDto())
                .build();
    }
}
