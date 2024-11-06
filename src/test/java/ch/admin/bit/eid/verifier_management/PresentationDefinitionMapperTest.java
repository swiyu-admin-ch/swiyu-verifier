package ch.admin.bit.eid.verifier_management;

import ch.admin.bit.eid.verifier_management.mappers.PresentationDefinitionMapper;
import ch.admin.bit.eid.verifier_management.mocks.FormatAlgorithmMocks;
import ch.admin.bit.eid.verifier_management.mocks.InputDescriptorMocks;
import ch.admin.bit.eid.verifier_management.models.PresentationDefinition;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PresentationDefinitionMapperTest {

    @Test
    void testMap() {
        PresentationDefinitionDto dto = PresentationDefinitionDto.builder()
                .id(UUID.randomUUID().toString())
                .name("Test Name")
                .purpose("Test Purpose")
                .format(FormatAlgorithmMocks.createFormatAlgorithmDto())
                .inputDescriptors(List.of(InputDescriptorMocks.getInputDescriptorDto()))
                .build();

        PresentationDefinition model = PresentationDefinitionMapper.map(dto);

        assertNotNull(model);
        assertEquals(dto.getId(), model.getId());
        assertEquals(dto.getName(), model.getName());
        assertEquals(dto.getPurpose(), model.getPurpose());
        assertEquals(dto.getFormat(), model.getFormat());
        assertEquals(dto.getInputDescriptors(), model.getInputDescriptors());
    }

    @Test
    void testMap_NullDto() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            PresentationDefinitionMapper.map(null);
        });

        assertEquals("PresentationDefinitionDto must not be null", exception.getMessage());
    }

    @Test
    void testToDto() {
        PresentationDefinition model = PresentationDefinition.builder()
                .id(UUID.randomUUID().toString())
                .name("Test Name")
                .purpose("Test Purpose")
                .format(FormatAlgorithmMocks.createFormatAlgorithmDto())
                .inputDescriptors(List.of(InputDescriptorMocks.getInputDescriptorDto()))
                .build();

        PresentationDefinitionDto dto = PresentationDefinitionMapper.toDto(model);

        assertNotNull(dto);
        assertEquals(model.getId(), dto.getId());
        assertEquals(model.getName(), dto.getName());
        assertEquals(model.getPurpose(), dto.getPurpose());
        assertEquals(model.getFormat(), dto.getFormat());
        assertEquals(model.getInputDescriptors(), dto.getInputDescriptors());
    }

    @Test
    void testToDto_NullModel() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            PresentationDefinitionMapper.toDto(null);
        });

        assertEquals("Presentation must not be null", exception.getMessage());
    }
}
