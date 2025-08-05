package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.api.definition.PresentationDefinitionDto;
import ch.admin.bj.swiyu.verifier.api.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ManagementServiceTest {

    private ManagementRepository repository;
    private ApplicationProperties applicationProperties;
    private ManagementService service;
    private UUID id;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        repository = mock(ManagementRepository.class);
        applicationProperties = mock(ApplicationProperties.class);
        service = new ManagementService(repository, applicationProperties);
    }

    @Test
    void createVerificationManagement_thenSuccess() {
        var presentationDefinitionDto = mock(PresentationDefinitionDto.class);
        var presentationDefinition = mock(PresentationDefinition.class);
        CreateVerificationManagementDto requestDto = new CreateVerificationManagementDto(
                List.of("did:example:123"),
                null,
                false,
                presentationDefinitionDto,
                null
        );
        var management = mock(Management.class);
        when(repository.save(any(Management.class))).thenReturn(management);

        try(MockedStatic<ManagementMapper> managementMapper = mockStatic(ManagementMapper.class)) {
            managementMapper.when(() -> ManagementMapper.toPresentationDefinition(any(PresentationDefinitionDto.class)))
                    .thenReturn(presentationDefinition);
            managementMapper.when(() -> ManagementMapper.toManagementResponseDto(any(Management.class), any()))
                    .thenReturn(mock(ch.admin.bj.swiyu.verifier.api.management.ManagementResponseDto.class));

            service.createVerificationManagement(requestDto);

            managementMapper.verify(() -> ManagementMapper.toManagementResponseDto(management, applicationProperties), times(1));
        }

        verify(repository).save(any(Management.class));
    }

    @Test
    void createVerificationManagementWithNullRequest_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> service.createVerificationManagement(null));
    }

    @Test
    void createVerificationManagementWithNullPresentation_throwsException() {
        var request = mock(CreateVerificationManagementDto.class);
        when(request.presentationDefinition()).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.createVerificationManagement(request));
    }

    @Test
    void getManagement_thenSuccess() {
        var management = mock(Management.class);
        when(management.isExpired()).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(management));

        try(MockedStatic<ManagementMapper> managementMapper = mockStatic(ManagementMapper.class)) {
            managementMapper.when(() -> ManagementMapper.toManagementResponseDto(management, applicationProperties))
                    .thenReturn(mock(ch.admin.bj.swiyu.verifier.api.management.ManagementResponseDto.class));

            service.getManagement(id);
            managementMapper.verify(() -> ManagementMapper.toManagementResponseDto(management, applicationProperties), times(1));
        }

        verify(repository, never()).deleteById(any());
    }

    @Test
    void getManagement_throwsException() {
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(VerificationNotFoundException.class, () -> service.getManagement(id));
    }

    @Test
    void getManagementWithExpired_shouldDelete() {
        var management = mock(Management.class);
        when(management.isExpired()).thenReturn(true);
        when(management.getId()).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.of(management));

        service.getManagement(id);
        verify(repository).deleteById(id);
    }

    @Test
    void removeExpiredManagements_shouldDelete() {
        service.removeExpiredManagements();
        verify(repository).deleteByExpiresAtIsBefore(anyLong());
    }
}