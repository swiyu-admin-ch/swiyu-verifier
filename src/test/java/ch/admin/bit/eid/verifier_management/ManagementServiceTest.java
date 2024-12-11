package ch.admin.bit.eid.verifier_management;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import ch.admin.bit.eid.verifier_management.exceptions.VerificationNotFoundException;
import ch.admin.bit.eid.verifier_management.models.Management;
import ch.admin.bit.eid.verifier_management.models.dto.CreateVerificationManagementDto;
import ch.admin.bit.eid.verifier_management.repositories.ManagementRepository;
import ch.admin.bit.eid.verifier_management.services.ManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// @SpringBootTest
public class ManagementServiceTest {

    @Mock
    private ManagementRepository repository;

    @InjectMocks
    private ManagementService managementService;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getManagement_ShouldReturnManagement_WhenFound() {
        var management = Management.builder()
                .id(UUID.randomUUID())
                .state(VerificationStatusEnum.PENDING)
                .expiresAt(System.currentTimeMillis() + 10000)
                .build();

        when(repository.findById(management.getId())).thenReturn(Optional.of(management));

        Management result = managementService.getManagement(management.getId());

        assertNotNull(result);
        assertEquals(management.getId(), result.getId());
        verify(repository, times(0)).deleteById(management.getId());
    }

    @Test
    void getManagement_ShouldThrowException() {
        var management = Management.builder()
                .id(UUID.randomUUID())
                .state(VerificationStatusEnum.PENDING)
                .build();

        when(repository.findById(management.getId())).thenReturn(Optional.empty());

        assertThrows(VerificationNotFoundException.class, () -> managementService.getManagement(management.getId()));
    }

    @Test
    void getManagement_ShouldDeleteEntryAfterPending() {
        var management = Management.builder()
                .id(UUID.randomUUID())
                .state(VerificationStatusEnum.SUCCESS)
                .build();

        when(repository.findById(management.getId())).thenReturn(Optional.of(management));

        managementService.getManagement(management.getId());
        verify(repository, times(1)).deleteById(management.getId());
    }

    @Test
    void createVerificationManagement_WithNullRequestNonce_thenIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> managementService.createVerificationManagement(null));
    }

    @Test
    void createVerificationManagement_WithNullPresentation_thenIllegalArgumentException() {
        var nullPresentationInRequest = CreateVerificationManagementDto.builder()
                .presentationDefinition(null)
                .build();

        assertThrows(IllegalArgumentException.class, () -> managementService.createVerificationManagement(nullPresentationInRequest));
    }
}
