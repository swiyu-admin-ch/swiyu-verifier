package ch.admin.bj.swiyu.verifier.service.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationRejectionDto;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.service.PresentationVerificationStrategyRegistry;
import ch.admin.bj.swiyu.verifier.service.callback.CallbackEventProducer;
import ch.admin.bj.swiyu.verifier.service.management.ManagementService;
import ch.admin.bj.swiyu.verifier.service.management.ManagementTransactionalService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.DcqlPresentationVerificationService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.PresentationSubmissionService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.PresentationVerificationService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.PresentationVerificationUsecase;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.LegacyPresentationVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerificationStrategy;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.getPresentationSubmissionString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresentationVerificationUsecaseTest {

    private PresentationVerificationUsecase presentationVerificationUsecase;
    private CallbackEventProducer callbackEventProducer;
    private Management managementEntity;
    private UUID managementId;
    private ManagementRepository managementRepository;
    private LegacyPresentationVerifier legacyPresentationVerifier;
    private PresentationVerifier presentationVerifier;
    private DcqlPresentationVerificationService dcqlPresentationVerificationService;
    private ObjectMapper objectMapper;
    private PresentationVerificationStrategyRegistry strategyRegistry;

    @BeforeEach
    void setUp() {
        managementRepository = mock(ManagementRepository.class);
        ApplicationProperties applicationProperties = mock(ApplicationProperties.class);
        ManagementTransactionalService managementTransactionalService = new ManagementTransactionalService(managementRepository, applicationProperties);
        ManagementService managementService = new ManagementService(applicationProperties, managementTransactionalService);

        objectMapper = new ObjectMapper();
        callbackEventProducer = mock(CallbackEventProducer.class);
        legacyPresentationVerifier = mock(LegacyPresentationVerifier.class);
        presentationVerifier = mock(PresentationVerifier.class);
        dcqlPresentationVerificationService = mock(DcqlPresentationVerificationService.class);
        strategyRegistry = mock(PresentationVerificationStrategyRegistry.class);

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        var submissionService = new PresentationSubmissionService(validator);
        var presentationVerificationService = new PresentationVerificationService(strategyRegistry, submissionService);

        presentationVerificationUsecase = new PresentationVerificationUsecase(
                callbackEventProducer,
                dcqlPresentationVerificationService,
                presentationVerificationService,
                managementService
        );

        managementEntity = mock(Management.class);
        managementId = UUID.randomUUID();

        // Default: session is PENDING, not expired → claimSessionForProcessing succeeds
        when(managementRepository.findById(managementId)).thenReturn(Optional.of(managementEntity));
        when(managementEntity.isExpired()).thenReturn(false);
        when(managementEntity.isVerificationPending()).thenReturn(true);
        when(managementEntity.getExpiresAt()).thenReturn(System.currentTimeMillis() + 900_000L);
        when(managementEntity.getId()).thenReturn(managementId);
        when(managementEntity.isProcessStillOpen()).thenReturn(true);
    }

    @Test
    void receiveVerificationPresentation_thenSuccess() throws JsonProcessingException {
        var vpToken = getVpToken();
        var mockRequest = getMockRequest(vpToken, getPresentationSubmissionString(UUID.randomUUID()));

        when(legacyPresentationVerifier.verify(vpToken, managementEntity)).thenReturn("credential-data");
        // stub registry with a strategy delegating to stringPresentationVerifier
        when(strategyRegistry.getStrategy(anyString())).thenReturn(new PresentationVerificationStrategy() {
            @Override
            public String verify(String vpToken, Management managementEntity, ch.admin.bj.swiyu.verifier.dto.submission.PresentationSubmissionDto presentationSubmission) {
                return legacyPresentationVerifier.verify(vpToken, managementEntity);
            }
            @Override
            public String getSupportedFormat() { return "vc+sd-jwt"; }
        });

        presentationVerificationUsecase.receiveVerificationPresentation(managementId, mockRequest);

        verify(managementEntity).verificationSucceeded("credential-data");
        verify(callbackEventProducer).produceEvent(managementId);
    }

    @Test
    void receiveDcqlVerificationPresentation_withoutHolderRequestedHolderBinding_thenSuccess() throws JsonProcessingException {
        var credentialRequestId = "TestIdRequest";
        var dcqlQuery = getDcqlQuery(credentialRequestId, false);
        var vpToken = getVpToken();
        var request = new VerificationPresentationDCQLRequestDto(Map.of(credentialRequestId, List.of(vpToken)));
        var sdJwt = mockVerifySdJwt(vpToken);
        when(managementEntity.getDcqlQuery()).thenReturn(dcqlQuery);

        // Stub SdjwtPresentationVerifier to return our prepared SdJwt when called from DcqlPresentationVerificationService
        var requestedCredential = dcqlQuery.getCredentials().getFirst();
        when(presentationVerifier.verify(Mockito.eq(vpToken), Mockito.eq(managementEntity), Mockito.eq(requestedCredential)))
                .thenReturn(sdJwt);

        var expectedVerificationSucceededData = objectMapper.writeValueAsString(Map.of(credentialRequestId, List.of(sdJwt.getClaims().getClaims())));
        when(dcqlPresentationVerificationService.process(managementEntity, request)).thenReturn(expectedVerificationSucceededData);

        assertDoesNotThrow(() -> presentationVerificationUsecase.receiveVerificationPresentationDCQL(managementId, request));
        verify(managementEntity).verificationSucceeded(expectedVerificationSucceededData);
        verify(callbackEventProducer).produceEvent(managementId);
    }

    @Test
    void receiveVerificationPresentation_clientRejected() {
        VerificationPresentationRejectionDto request = mock(VerificationPresentationRejectionDto.class);
        when(request.getErrorDescription()).thenReturn("User cancelled");

        presentationVerificationUsecase.receiveVerificationPresentationClientRejection(managementId, request);

        verify(managementEntity).verificationFailedDueToClientRejection("User cancelled");
        verify(callbackEventProducer).produceEvent(managementId);
        verify(managementEntity, never()).verificationSucceeded(any());
    }

    /**
     * Simulates a session that is already IN_PROGRESS/FAILED/SUCCESS (not PENDING anymore).
     * claimSessionForProcessing must throw VerificationException.
     * This covers both the TOCTOU race condition and the sequential replay scenario.
     */
    @Test
    void receiveVerificationPresentation_sessionAlreadyClaimed_thenThrowsProcessClosedException() {
        when(managementEntity.isVerificationPending()).thenReturn(false);

        assertThrows(VerificationException.class, () ->
                presentationVerificationUsecase.receiveVerificationPresentation(managementId,
                        mock(VerificationPresentationRequestDto.class)));

        verify(managementEntity, never()).verificationSucceeded(any());
        verify(managementEntity, times(1)).verificationFailed(any(), any());
        verify(callbackEventProducer, times(1)).produceEvent(any());
    }

    /**
     * Simulates a session that is already claimed for the DCQL flow.
     */
    @Test
    void receiveVerificationPresentationDCQL_sessionAlreadyClaimed_thenThrowsProcessClosedException() {
        when(managementEntity.isProcessStillOpen()).thenReturn(false);

        assertThrows(VerificationException.class, () ->
                presentationVerificationUsecase.receiveVerificationPresentationDCQL(managementId,
                        new VerificationPresentationDCQLRequestDto(Map.of("credId", List.of("token")))));

        verify(managementEntity, never()).verificationSucceeded(any());
        verify(managementEntity, times(1)).verificationFailed(any(), any());
        verify(callbackEventProducer, times(1)).produceEvent(any());
    }

    /**
     * Simulates an expired session: entity exists but expiresAt is in the past.
     * claimSessionForProcessing must throw ProcessClosedException.
     */
    @Test
    void receiveVerificationPresentation_sessionExpiredAndDeleted_thenThrowsProcessClosedException() {
        when(managementEntity.getExpiresAt()).thenReturn(System.currentTimeMillis() - 1000L);

        assertThrows(VerificationException.class, () ->
                presentationVerificationUsecase.receiveVerificationPresentation(managementId,
                        mock(VerificationPresentationRequestDto.class)));

        verify(managementEntity, never()).verificationSucceeded(any());
        verify(managementEntity, times(1)).verificationFailed(any(), any());
        verify(callbackEventProducer, times(1)).produceEvent(any());
    }

    @Test
    void receiveVerificationPresentation_verificationException() throws JsonProcessingException {
        var mockRequest = getMockRequestNoVpToken();

        var exception = assertThrows(VerificationException.class, () ->
                presentationVerificationUsecase.receiveVerificationPresentation(managementId, mockRequest));

        assertEquals(AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM, exception.getErrorResponseCode());
        verify(managementEntity).verificationFailed(any(), any());
        verify(callbackEventProducer).produceEvent(managementId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"User declined the verification request", ""})
    @NullSource
    void receiveVerificationPresentationClientRejection_thenSuccess(String errorDescription) {
        VerificationPresentationRejectionDto rejectionRequest = mock(VerificationPresentationRejectionDto.class);
        when(rejectionRequest.getErrorDescription()).thenReturn(errorDescription);

        presentationVerificationUsecase.receiveVerificationPresentationClientRejection(managementId, rejectionRequest);

        verify(managementEntity).verificationFailedDueToClientRejection(errorDescription);
        verify(callbackEventProducer).produceEvent(managementId);
        verify(managementEntity, never()).verificationSucceeded(any());
    }

    @Test
    void receiveVerificationPresentationClientRejection_processClosed_thenThrowsException() {
        when(managementEntity.isExpired()).thenReturn(true);
        when(managementEntity.isProcessStillOpen()).thenReturn(false);
        VerificationPresentationRejectionDto rejectionRequest = mock(VerificationPresentationRejectionDto.class);
        when(rejectionRequest.getErrorDescription()).thenReturn("User cancelled");

        assertThrows(ProcessClosedException.class, () ->
                presentationVerificationUsecase.receiveVerificationPresentationClientRejection(managementId, rejectionRequest));

        verify(callbackEventProducer).produceEvent(managementId);
        verify(managementEntity, never()).verificationFailedDueToClientRejection(any());
    }

    @Test
    void receiveVerificationPresentationClientRejection_verificationNotPending_thenThrowsException() {
        when(managementEntity.isVerificationPending()).thenReturn(false);
        when(managementEntity.isProcessStillOpen()).thenReturn(false);
        VerificationPresentationRejectionDto rejectionRequest = mock(VerificationPresentationRejectionDto.class);
        when(rejectionRequest.getErrorDescription()).thenReturn("User cancelled");

        assertThrows(ProcessClosedException.class, () ->
                presentationVerificationUsecase.receiveVerificationPresentationClientRejection(managementId, rejectionRequest));

        verify(callbackEventProducer).produceEvent(managementId);
        verify(managementEntity, never()).verificationFailedDueToClientRejection(any());
    }

    /**
     * REGRESSION — Sequential Replay Attack:
     * <p>
     * Reproduces the original bug where the same VP token could be submitted twice
     * to the same session. Before the fix, both submissions would call
     * {@code verificationSucceeded()} because there was no state guard.
     * <p>
     * After the fix, the second submission sees {@code isVerificationPending() = false}
     * (Hibernate has already committed IN_PROGRESS) and is rejected with
     * {@link ProcessClosedException} — without touching the DB or firing a callback.
     */
    @Test
    void regressionTest_sequentialReplay_secondSubmissionMustBeRejected() {
        var credentialRequestId = "replayCredential";
        var request = new VerificationPresentationDCQLRequestDto(Map.of(credentialRequestId, List.of("anyToken")));
        var successData = "{\"replayCredential\":[{}]}";

        when(dcqlPresentationVerificationService.process(managementEntity, request)).thenReturn(successData);

        // First submission — must succeed and fire callback
        assertDoesNotThrow(() ->
                presentationVerificationUsecase.receiveVerificationPresentationDCQL(managementId, request));
        verify(managementEntity, times(1)).verificationSucceeded(successData);
        verify(callbackEventProducer, times(1)).produceEvent(managementId);

        // Simulate DB state after first commit: Hibernate has set state = IN_PROGRESS
        when(managementEntity.isProcessStillOpen()).thenReturn(false);

        // Second submission (replay) — must be rejected, no DB write, no callback
        assertThrows(VerificationException.class, () ->
                presentationVerificationUsecase.receiveVerificationPresentationDCQL(managementId, request));// still only 1
    }

    /**
     * REGRESSION — Concurrent Race Condition (TOCTOU):
     * <p>
     * Reproduces the original bug where two threads submitted a VP token for the same
     * session simultaneously. Before the fix, both threads passed the PENDING check
     * and both called {@code verificationSucceeded()}.
     * <p>
     * After the fix, {@code @Version} on {@link Management} causes Hibernate to throw
     * {@code ObjectOptimisticLockingFailureException} for the losing thread at commit time.
     * The {@link CyclicBarrier} forces both threads to the submission point simultaneously.
     */
    @Test
    void regressionTest_concurrentDuplicateSubmission_exactlyOneThreadMustSucceed() throws Exception {
        var credentialRequestId = "raceCredential";
        var request = new VerificationPresentationDCQLRequestDto(Map.of(credentialRequestId, List.of("anyToken")));
        var successData = "{\"raceCredential\":[{}]}";

        when(dcqlPresentationVerificationService.process(managementEntity, request)).thenReturn(successData);

        // Simulate Hibernate @Version: first commit succeeds, second throws OptimisticLockException
        var callCount = new AtomicInteger(0);
        doAnswer(inv -> {
            if (callCount.incrementAndGet() > 1) {
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Management.class, managementId);
            }
            return null; // first call succeeds
        }).when(managementEntity).claimForProcessing();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        CyclicBarrier startGate = new CyclicBarrier(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> threadA = pool.submit(() -> {
                try {
                    startGate.await();
                    presentationVerificationUsecase.receiveVerificationPresentationDCQL(managementId, request);
                    successCount.incrementAndGet();
                } catch (ProcessClosedException | VerificationException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                }
            });
            Future<?> threadB = pool.submit(() -> {
                try {
                    startGate.await();
                    presentationVerificationUsecase.receiveVerificationPresentationDCQL(managementId, request);
                    successCount.incrementAndGet();
                } catch (ProcessClosedException | VerificationException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                }
            });
            threadA.get();
            threadB.get();
        } finally {
            pool.shutdown();
        }

        assertEquals(1, successCount.get(),
                "Exactly one concurrent submission must succeed — before the fix both would succeed");
        assertEquals(1, rejectedCount.get(),
                "Exactly one concurrent submission must be rejected");
        verify(managementEntity, times(1)).verificationSucceeded(successData);
    }

    // --- helper methods ---

    private VerificationPresentationRequestDto getMockRequestNoVpToken() throws JsonProcessingException {
        return getMockRequest("", getPresentationSubmissionString(UUID.randomUUID()));
    }

    private VerificationPresentationRequestDto getMockRequest(String vpToken, String presentationSubmission) {
        return new VerificationPresentationRequestDto(vpToken, presentationSubmission);
    }

    private String getVpToken() {
        return new SDJWTCredentialMock().createSDJWTMock();
    }

    private SdJwt mockVerifySdJwt(String vpTokenSdJwt) {
        var sdJwt = new SdJwt(vpTokenSdJwt);
        var parsed = assertDoesNotThrow(() -> SignedJWT.parse(sdJwt.getJwt()));
        var claims = assertDoesNotThrow(parsed::getJWTClaimsSet);
        var disclosures = sdJwt.getDisclosures();
        var claimBuilder = new JWTClaimsSet.Builder(claims);
        disclosures.forEach(disclosure -> claimBuilder.claim(disclosure.getClaimName(), disclosure.getClaimValue()));
        sdJwt.setClaims(claimBuilder.build());
        sdJwt.setHeader(parsed.getHeader());
        return sdJwt;
    }

    /**
     * Create a dcql query matching the default vp token
     */
    private DcqlQuery getDcqlQuery(String dcqlCredentialId, boolean requireCryptographicHolderBinding) {
        var requestedCredential = new DcqlCredential(
                dcqlCredentialId,
                "dc+sd-jwt",
                new DcqlCredentialMeta(null, List.of(SDJWTCredentialMock.DEFAULT_VCT), null),
                List.of(
                        new DcqlClaim(null, List.of("birthdate"), null),
                        new DcqlClaim(null, List.of("last_name"), null)),
                requireCryptographicHolderBinding,
                false);
        return new DcqlQuery(List.of(requestedCredential), null);
    }
}

