package ch.admin.bit.eid.verifier_management;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import ch.admin.bit.eid.verifier_management.mappers.ManagementMapper;
import ch.admin.bit.eid.verifier_management.mappers.PresentationDefinitionMapper;
import ch.admin.bit.eid.verifier_management.mocks.FormatAlgorithmMocks;
import ch.admin.bit.eid.verifier_management.mocks.InputDescriptorMocks;
import ch.admin.bit.eid.verifier_management.models.Management;
import ch.admin.bit.eid.verifier_management.models.dto.ManagementResponseDto;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagementMapperTest {

    UUID id = UUID.randomUUID();
    VerificationStatusEnum state = VerificationStatusEnum.PENDING;
    String oid4vpUrl = "https://example.com";

    @Test
    void testToDto() {
        Management management = mock(Management.class);
        when(management.getId()).thenReturn(id);
        when(management.getRequestNonce()).thenReturn("test-nonce");
        when(management.getState()).thenReturn(state);
        when(management.getRequestedPresentation()).thenReturn(null);
        when(management.getWalletResponse()).thenReturn(null);

        String expectedVerificationUrl = "%s/request-object/%s".formatted(oid4vpUrl, id);

        ManagementResponseDto dto = ManagementMapper.toDto(management, oid4vpUrl);

        assertNotNull(dto);
        assertEquals(id, dto.getId());
        assertEquals("test-nonce", dto.getRequestNonce());
        assertEquals(state, dto.getState());
        assertNull(dto.getPresentationDefinition());
        assertNull(dto.getWalletResponse());
        assertEquals(expectedVerificationUrl, dto.getVerificationUrl());
    }

    @Test
    void testToDto_WithPresentationDefinition() {

        var expectedFormat = FormatAlgorithmMocks.createFormatAlgorithmDto();
        Management management = mock(Management.class);
        when(management.getId()).thenReturn(id);
        when(management.getRequestNonce()).thenReturn("test-nonce");
        when(management.getState()).thenReturn(state);

        PresentationDefinitionDto presentationDefinitionDto = PresentationDefinitionDto.builder()
                .id("presentation-id")
                .name("presentation-name")
                .purpose("presentation-purpose")
                .format(expectedFormat)
                .inputDescriptors(List.of(InputDescriptorMocks.getInputDescriptorDto()))
                .build();

        when(management.getRequestedPresentation()).thenReturn(PresentationDefinitionMapper.map(presentationDefinitionDto));
        when(management.getWalletResponse()).thenReturn(null);

        String expectedVerificationUrl = "%s/request-object/%s".formatted(oid4vpUrl, id);

        ManagementResponseDto dto = ManagementMapper.toDto(management, oid4vpUrl);

        assertNotNull(dto);
        assertEquals(id, dto.getId());
        assertEquals("test-nonce", dto.getRequestNonce());
        assertEquals(state, dto.getState());
        assertNotNull(dto.getPresentationDefinition());
        assertEquals("presentation-id", dto.getPresentationDefinition().getId());
        assertEquals("presentation-name", dto.getPresentationDefinition().getName());
        assertEquals("presentation-purpose", dto.getPresentationDefinition().getPurpose());
        assertEquals(expectedFormat, dto.getPresentationDefinition().getFormat());
        assertNull(dto.getWalletResponse());
        assertEquals(expectedVerificationUrl, dto.getVerificationUrl());
    }

    @Test
    void testToDto_NullManagement() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ManagementMapper.toDto(null, "http://example.com");
        });

        assertEquals("Management must not be null", exception.getMessage());
    }
}
