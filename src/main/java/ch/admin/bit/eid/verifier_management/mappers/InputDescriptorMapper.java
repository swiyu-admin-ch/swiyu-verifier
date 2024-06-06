package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.InputDescriptor;
import ch.admin.bit.eid.verifier_management.models.dto.InputDescriptorDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@UtilityClass
public class InputDescriptorMapper {

    public static List<InputDescriptor> InputDescriptorDTOsToInputDescriptors(List<InputDescriptorDto> dtos) {
        return dtos.stream().map(InputDescriptorMapper::toModel).toList();
    }

    public static InputDescriptor toModel(InputDescriptorDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("InputDescriptor cannot be null");
        }

        String constraints = MapToJsonString(dto.getConstraints());
        String formats = MapToJsonString(dto.getFormat());

        InputDescriptor descriptor = InputDescriptor.builder()
                .id(dto.getId() == null ? dto.getId() : UUID.randomUUID())
                .constraints(constraints)
                .format(formats)
                .group(dto.getGroup())
                .name(dto.getName())
                .build();

        return descriptor;
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
                .group(inputDescriptor.getGroup())
                .format(JsonStringToMap(inputDescriptor.getFormat()))
                .constraints(JsonStringToMap(inputDescriptor.getConstraints()))
                .build();
    }

    private static Map<String, Object> JsonStringToMap(String jsonString) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return objectMapper.readValue(jsonString, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    private static String MapToJsonString(Map<?, ?> map) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }
}
