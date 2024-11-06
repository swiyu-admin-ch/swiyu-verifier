package ch.admin.bit.eid.verifier_management;

import ch.admin.bit.eid.verifier_management.mappers.InputDescriptorMapper;
import ch.admin.bit.eid.verifier_management.mocks.FormatAlgorithmMocks;
import ch.admin.bit.eid.verifier_management.models.InputDescriptor;
import ch.admin.bit.eid.verifier_management.models.dto.ConstraintDto;
import ch.admin.bit.eid.verifier_management.models.dto.InputDescriptorDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InputDescriptorMapperTest {

    @Test
    void testToModel() {
        InputDescriptorDto dto = InputDescriptorDto.builder()
                .id(UUID.randomUUID().toString())
                .name("Test Name")
                .purpose("Test Purpose")
                .constraints(ConstraintDto.builder().build())
                .format(FormatAlgorithmMocks.createFormatAlgorithmDto())
                .build();

        InputDescriptor model = InputDescriptorMapper.toModel(dto);

        assertNotNull(model);
        assertEquals(dto.getId(), model.getId());
        assertEquals(dto.getName(), model.getName());
        assertEquals(dto.getPurpose(), model.getPurpose());
        assertEquals(dto.getConstraints(), model.getConstraints());
        assertEquals(dto.getFormat(), model.getFormat());
    }

    @Test
    void testToModel_NullDto() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            InputDescriptorMapper.toModel(null);
        });

        assertEquals("InputDescriptor cannot be null", exception.getMessage());
    }

    @Test
    void testToDto() {
        InputDescriptor model = InputDescriptor.builder()
                .id(UUID.randomUUID().toString())
                .name("Test Name")
                .purpose("Test Purpose")
                .constraints(ConstraintDto.builder().build())
                .format(FormatAlgorithmMocks.createFormatAlgorithmDto())
                .build();

        InputDescriptorDto dto = InputDescriptorMapper.toDto(model);

        assertNotNull(dto);
        assertEquals(model.getId(), dto.getId());
        assertEquals(model.getName(), dto.getName());
        assertEquals(model.getPurpose(), dto.getPurpose());
        assertEquals(model.getConstraints(), dto.getConstraints());
        assertEquals(model.getFormat(), dto.getFormat());
    }

    @Test
    void testToDto_NullModel() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            InputDescriptorMapper.toDto(null);
        });

        assertEquals("InputDescriptor cannot be null", exception.getMessage());
    }

    @Test
    void testToDTOs() {
        InputDescriptor model1 = InputDescriptor.builder()
                .id(UUID.randomUUID().toString())
                .name("Test Name 1")
                .purpose("Test Purpose 1")
                .constraints(ConstraintDto.builder().build())
                .format(FormatAlgorithmMocks.createFormatAlgorithmDto())
                .build();

        InputDescriptor model2 = InputDescriptor.builder()
                .id(UUID.randomUUID().toString())
                .name("Test Name 2")
                .purpose("Test Purpose 2")
                .constraints(ConstraintDto.builder().build())
                .format(FormatAlgorithmMocks.createFormatAlgorithmDto())
                .build();

        List<InputDescriptorDto> dtos = InputDescriptorMapper.toDTOs(List.of(model1, model2));

        assertNotNull(dtos);
        assertEquals(2, dtos.size());
        assertEquals(model1.getId(), dtos.get(0).getId());
        assertEquals(model2.getId(), dtos.get(1).getId());
    }
}
