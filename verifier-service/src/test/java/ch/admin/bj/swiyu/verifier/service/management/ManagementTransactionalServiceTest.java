package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.domain.VerificationResultData;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertDoesNotThrow(() -> managementTransactionalService.markVerificationDone(UUID.randomUUID(), VerificationResultData.builder().build()));
        verify(mockManagement, times(1)).verificationDone(any());
    }

    @Test
    void markVerificationFailed() {
        var mockManagement = spy(Management.class);
        when(mockManagement.getState()).thenReturn(VerificationStatus.IN_PROGRESS);
        when(mockRepository.findById(any())).thenReturn(Optional.of(mockManagement));
        assertDoesNotThrow(() -> managementTransactionalService.markVerificationException(UUID.randomUUID(), mock(VerificationException.class)));
        verify(mockManagement, times(1)).verificationFailed(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = {"PENDING", "SUCCESS", "FAILED"})
    void markVerificationSucceeded_whenNotInProgress(VerificationStatus status) {
        var mockManagement = mock(Management.class);
        when(mockManagement.getState()).thenReturn(status);
        when(mockRepository.findById(any())).thenReturn(Optional.of(mockManagement));
        assertThrows(ProcessClosedException.class, () -> managementTransactionalService.markVerificationDone(UUID.randomUUID(), VerificationResultData.builder().build()));
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = {"PENDING", "SUCCESS", "FAILED"})
    void markVerificationFailed_whenNotInProgress(VerificationStatus status) {
        var mockManagement = mock(Management.class);
        when(mockManagement.getState()).thenReturn(status);
        when(mockRepository.findById(any())).thenReturn(Optional.of(mockManagement));

        assertThrows(ProcessClosedException.class, () -> managementTransactionalService.markVerificationException(UUID.randomUUID(), mock(VerificationException.class)));
    }

    @Test
    void findAndHandleExpiration_notFound_throwsVerificationNotFoundException() {
        UUID id = UUID.randomUUID();
        when(mockRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(VerificationNotFoundException.class,
                () -> managementTransactionalService.findAndHandleExpiration(id, UUID.randomUUID()));
    }

    @Test
    void findAndHandleExpiration_notExpiredWithoutRedirectURI_returnsManagement() {
        UUID id = UUID.randomUUID();
        var mockManagement = mock(Management.class);
        when(mockManagement.isExpired()).thenReturn(false);
        when(mockManagement.getRedirectURI()).thenReturn(null);
        when(mockRepository.findById(id)).thenReturn(Optional.of(mockManagement));

        var result = managementTransactionalService.findAndHandleExpiration(id, UUID.randomUUID());

        assertThat(result).isEqualTo(mockManagement);
        verify(mockRepository, never()).deleteById(any());
    }

    @Test
    void findAndHandleExpiration_expired_deletesAndThrowsVerificationNotFoundException() {
        UUID id = UUID.randomUUID();
        var mockManagement = mock(Management.class);
        when(mockManagement.getId()).thenReturn(id);
        when(mockManagement.isExpired()).thenReturn(true);
        when(mockRepository.findById(id)).thenReturn(Optional.of(mockManagement));

        assertThrows(VerificationNotFoundException.class,
                () -> managementTransactionalService.findAndHandleExpiration(id, UUID.randomUUID()));

        verify(mockRepository, times(1)).deleteById(id);
    }

    @Test
    void findAndHandleExpiration_withMatchingResponseCode_returnsManagement() {
        UUID id = UUID.randomUUID();
        UUID responseCode = UUID.randomUUID();
        var mockManagement = mock(Management.class);
        when(mockManagement.isExpired()).thenReturn(false);
        when(mockManagement.getRedirectURI()).thenReturn(URI.create("https://example.com/callback"));
        when(mockManagement.getResponseCode()).thenReturn(responseCode);
        when(mockRepository.findById(id)).thenReturn(Optional.of(mockManagement));

        var result = managementTransactionalService.findAndHandleExpiration(id, responseCode);

        assertThat(result).isEqualTo(mockManagement);
        verify(mockRepository, never()).deleteById(any());
    }

    @Test
    void findAndHandleExpiration_withMismatchedResponseCode_throwsIllegalArgumentException() {
        UUID id = UUID.randomUUID();
        UUID storedResponseCode = UUID.randomUUID();
        UUID providedResponseCode = UUID.randomUUID();
        var mockManagement = mock(Management.class);
        when(mockManagement.getId()).thenReturn(id);
        when(mockManagement.isExpired()).thenReturn(false);
        when(mockManagement.getRedirectURI()).thenReturn(URI.create("https://example.com/callback"));
        when(mockManagement.getResponseCode()).thenReturn(storedResponseCode);
        when(mockRepository.findById(id)).thenReturn(Optional.of(mockManagement));

        assertThrows(IllegalArgumentException.class,
                () -> managementTransactionalService.findAndHandleExpiration(id, providedResponseCode));
    }
}