package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.InputDescriptor;
import ch.admin.bit.eid.verifier_management.models.dto.InputDescriptorDto;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.UUID;

@UtilityClass
public class InputDescriptorMapper {

    public static InputDescriptor toModel(InputDescriptorDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("InputDescriptor cannot be null");
        }

        return InputDescriptor.builder()
                .id(dto.getId() != null ? dto.getId() : UUID.randomUUID().toString())
                .constraints(dto.getConstraints())
                .name(dto.getName())
                .format(dto.getFormat())
                .purpose(dto.getPurpose())
                .build();
    }

    public static List<InputDescriptorDto> toDTOs(List<InputDescriptor> inputDescriptors) {
        return inputDescriptors.stream().map(InputDescriptorMapper::toDto).toList();
    }

    public static InputDescriptorDto toDto(InputDescriptor inputDescriptor) {
        if (inputDescriptor == null) {
            throw new IllegalArgumentException("InputDescriptor cannot be null");
        }

        return InputDescriptorDto.builder()
                .id(inputDescriptor.getId())
                .name(inputDescriptor.getName())
                .purpose(inputDescriptor.getPurpose())
                .constraints(inputDescriptor.getConstraints())
                .build();
    }
}
