package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ManagementTransactionalServiceTest {

    private ManagementTransactionalService managementTransactionalService;
    private ManagementRepository mockRepository;

    @BeforeEach
    void setup() {
        var applicationProperties = mock(ApplicationProperties.class);
        mockRepository = mock(ManagementRepository.class);
        managementTransactionalService = new ManagementTransactionalService(mockRepository, applicationProperties);
    }

    @Test
    void markVerificationSucceeded() {
        var mockManagement = spy(Management.class);
        when(mockManagement.getState()).thenReturn(VerificationStatus.IN_PROGRESS);
        when(mockRepository.findById(any())).thenReturn(Optional.of(mockManagement));
        assertDoesNotThrow(() -> managementTransactionalService.markVerificationSucceeded(UUID.randomUUID(), "Some Test Data"));
        verify(mockManagement, times(1)).verificationSucceeded(any());
    }

    @Test
    void markVerificationFailed() {
        var mockManagement = spy(Management.class);
        when(mockManagement.getState()).thenReturn(VerificationStatus.IN_PROGRESS);
        when(mockRepository.findById(any())).thenReturn(Optional.of(mockManagement));
        assertDoesNotThrow(() -> managementTransactionalService.markVerificationFailed(UUID.randomUUID(), mock(VerificationException.class)));
        verify(mockManagement, times(1)).verificationFailed(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = {"PENDING", "SUCCESS", "FAILED"})
    void markVerificationSucceeded_whenNotInProgress(VerificationStatus status) {
        var mockManagement = mock(Management.class);
        when(mockManagement.getState()).thenReturn(status);
        when(mockRepository.findById(any())).thenReturn(Optional.of(mockManagement));
        assertThrows(ProcessClosedException.class, () -> managementTransactionalService.markVerificationSucceeded(UUID.randomUUID(), "Some Test Data"));
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = {"PENDING", "SUCCESS", "FAILED"})
    void markVerificationFailed_whenNotInProgress(VerificationStatus status) {
        var mockManagement = mock(Management.class);
        when(mockManagement.getState()).thenReturn(status);
        when(mockRepository.findById(any())).thenReturn(Optional.of(mockManagement));

        assertThrows(ProcessClosedException.class, () -> managementTransactionalService.markVerificationFailed(UUID.randomUUID(), mock(VerificationException.class)));
    }
}