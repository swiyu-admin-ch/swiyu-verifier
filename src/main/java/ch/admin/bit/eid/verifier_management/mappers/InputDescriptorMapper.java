package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.InputDescriptor;
import ch.admin.bit.eid.verifier_management.models.dto.InputDescriptorDto;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.UUID;

@UtilityClass
public class InputDescriptorMapper {

    public static List<InputDescriptor> InputDescriptorDTOsToInputDescriptors(List<InputDescriptorDto> dtos) {
        return dtos.stream().map(InputDescriptorMapper::InputDescriptorDtoToInputDescriptor).toList();
    }

    public static InputDescriptor InputDescriptorDtoToInputDescriptor(InputDescriptorDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("InputDescriptor cannot be null");
        }

        return InputDescriptor.builder()
                .id(dto.getId() == null ? dto.getId() : UUID.randomUUID())
                .constraints(dto.getConstraints())
                .format(dto.getFormat())
                .name(dto.getName())
                .build();
    }
}
