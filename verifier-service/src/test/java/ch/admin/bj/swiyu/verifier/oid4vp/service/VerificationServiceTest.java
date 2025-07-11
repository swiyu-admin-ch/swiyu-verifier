package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.callback.WebhookService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.VerificationService;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.Optional;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.getPresentationSubmissionString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class VerificationServiceTest {

    private VerificationService verificationService;
    private WebhookService webhookService;

    private Management managementEntity;
    private UUID managementId;

    @BeforeEach
    void setUp() {
        VerificationProperties verificationProperties = mock(VerificationProperties.class);
        ManagementRepository managementRepository = mock(ManagementRepository.class);
        IssuerPublicKeyLoader issuerPublicKeyLoader = mock(IssuerPublicKeyLoader.class);
        StatusListReferenceFactory statusListReferenceFactory = mock(StatusListReferenceFactory.class);
        ObjectMapper objectMapper = new ObjectMapper();
        webhookService = mock(WebhookService.class);

        verificationService = new VerificationService(
                verificationProperties,
                managementRepository,
                issuerPublicKeyLoader,
                statusListReferenceFactory,
                objectMapper,
                webhookService
        );

        managementEntity = mock(Management.class);
        managementId = UUID.randomUUID();

        when(managementRepository.findById(managementId)).thenReturn(Optional.of(managementEntity));
        when(managementEntity.isExpired()).thenReturn(false);
        when(managementEntity.isVerificationPending()).thenReturn(true);
        when(managementEntity.getId()).thenReturn(managementId);
    }

    @Test
    void receiveVerificationPresentation_thenSuccess() throws JsonProcessingException {
        VerificationPresentationRequestDto request = mock(VerificationPresentationRequestDto.class);
        when(request.isClientRejection()).thenReturn(false);

        var mockRequest = getMockRequest(getVpToken(), getPresentationSubmissionString(UUID.randomUUID()));

        try (MockedConstruction<SdjwtCredentialVerifier> mocked = mockConstruction(SdjwtCredentialVerifier.class,
                (mock, context) -> when(mock.verifyPresentation()).thenReturn("credential-data"))) {
            verificationService.receiveVerificationPresentation(managementId, mockRequest);

            verify(managementEntity).verificationSucceeded("credential-data");
            verify(webhookService).produceEvent(managementId);
        }
    }

    @Test
    void receiveVerificationPresentation_clientRejected() {
        VerificationPresentationRequestDto request = mock(VerificationPresentationRequestDto.class);
        when(request.isClientRejection()).thenReturn(true);
        when(request.getError_description()).thenReturn("User cancelled");

        verificationService.receiveVerificationPresentation(managementId, request);

        verify(managementEntity).verificationFailedDueToClientRejection("User cancelled");
        verify(webhookService).produceEvent(managementId);
        verify(managementEntity, never()).verificationSucceeded(any());
    }

    @Test
    void receiveVerificationPresentation_processClosed() {
        when(managementEntity.isExpired()).thenReturn(true);
        VerificationPresentationRequestDto request = mock(VerificationPresentationRequestDto.class);

        assertThrows(ProcessClosedException.class, () ->
                verificationService.receiveVerificationPresentation(managementId, request));
        verify(webhookService).produceEvent(managementId);
    }

    @Test
    void receiveVerificationPresentation_verificationException() throws JsonProcessingException {
        VerificationPresentationRequestDto request = mock(VerificationPresentationRequestDto.class);
        when(request.isClientRejection()).thenReturn(false);

        var mockRequest = getMockRequestNoVpToken();

        var exception = assertThrows(VerificationException.class, () ->
                verificationService.receiveVerificationPresentation(managementId, mockRequest));

        assertEquals(AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM, exception.getErrorResponseCode());

        verify(managementEntity).verificationFailed(any(), any());
        verify(webhookService).produceEvent(managementId);
    }

    private VerificationPresentationRequestDto getMockRequestNoVpToken() throws JsonProcessingException {
        var presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        return getMockRequest("", presentationSubmission);
    }

    private VerificationPresentationRequestDto getMockRequest(String vpToken, String presentationSubmission) {
        return new VerificationPresentationRequestDto(vpToken, presentationSubmission, null, null);
    }

    private String getVpToken() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        return emulator.createSDJWTMock();
    }
}