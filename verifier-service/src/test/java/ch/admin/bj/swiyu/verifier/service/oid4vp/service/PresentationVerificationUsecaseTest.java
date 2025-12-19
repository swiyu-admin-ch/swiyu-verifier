package ch.admin.bj.swiyu.verifier.service.oid4vp.service;

import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationRejectionDto;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationRequestDto;
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
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.callback.CallbackEventProducer;
import ch.admin.bj.swiyu.verifier.service.management.ManagementService;
import ch.admin.bj.swiyu.verifier.service.management.ManagementTransactionalService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.PresentationSubmissionService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.PresentationVerificationService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.PresentationVerificationUsecase;
import ch.admin.bj.swiyu.verifier.service.PresentationVerificationStrategyRegistry;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerificationStrategy;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.LegacyPresentationVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.DcqlPresentationVerificationService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
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

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.getPresentationSubmissionString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresentationVerificationUsecaseTest {

    private PresentationVerificationUsecase presentationVerificationUsecase;
    private CallbackEventProducer callbackEventProducer;

    private Management managementEntity;
    private UUID managementId;
    private LegacyPresentationVerifier legacyPresentationVerifier;
    private PresentationVerifier presentationVerifier;
    private DcqlPresentationVerificationService dcqlPresentationVerificationService;
    private PresentationVerificationService presentationVerificationService;
    private ObjectMapper objectMapper;
    private PresentationVerificationStrategyRegistry strategyRegistry; // Registry für Format-Strategien
    private PresentationSubmissionService submissionService;

    @BeforeEach
    void setUp() {
        ManagementRepository managementRepository = mock(ManagementRepository.class);
        ApplicationProperties applicationProperties = mock(ApplicationProperties.class);
        ManagementTransactionalService managementTransactionalService = new ManagementTransactionalService(managementRepository, applicationProperties);
        ManagementService managementService = new ManagementService(applicationProperties, managementTransactionalService);

        objectMapper = new ObjectMapper();
        callbackEventProducer = mock(CallbackEventProducer.class);
        legacyPresentationVerifier = mock(LegacyPresentationVerifier.class);
        presentationVerifier = mock(PresentationVerifier.class);
        dcqlPresentationVerificationService = mock(DcqlPresentationVerificationService.class);
        strategyRegistry = mock(PresentationVerificationStrategyRegistry.class);
        // provide a real Validator to the submissionService to avoid NPE
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        submissionService = new PresentationSubmissionService(validator);
        presentationVerificationService = new PresentationVerificationService(
                strategyRegistry,
                submissionService
        );

        presentationVerificationUsecase = new PresentationVerificationUsecase(
                callbackEventProducer,
                dcqlPresentationVerificationService,
                presentationVerificationService,
                managementService
        );

        managementEntity = mock(Management.class);
        managementId = UUID.randomUUID();

        when(managementRepository.findById(managementId)).thenReturn(Optional.of(managementEntity));
        when(managementEntity.isExpired()).thenReturn(false);
        when(managementEntity.isVerificationPending()).thenReturn(true);
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

        when(dcqlPresentationVerificationService.process(Mockito.eq(managementEntity), Mockito.eq(request))).thenReturn(expectedVerificationSucceededData);

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

    @Test
    void receiveVerificationPresentation_processClosed() {
        when(managementEntity.isExpired()).thenReturn(true);
        when(managementEntity.isProcessStillOpen()).thenReturn(false);
        VerificationPresentationRequestDto request = mock(VerificationPresentationRequestDto.class);

        assertThrows(ProcessClosedException.class, () ->
                presentationVerificationUsecase.receiveVerificationPresentation(managementId, request));
        verify(callbackEventProducer).produceEvent(managementId);
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

    private VerificationPresentationRequestDto getMockRequestNoVpToken() throws JsonProcessingException {
        var presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        return getMockRequest("", presentationSubmission);
    }

    private VerificationPresentationRequestDto getMockRequest(String vpToken, String presentationSubmission) {
        return new VerificationPresentationRequestDto(vpToken, presentationSubmission);
    }

    private String getVpToken() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        return emulator.createSDJWTMock();
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