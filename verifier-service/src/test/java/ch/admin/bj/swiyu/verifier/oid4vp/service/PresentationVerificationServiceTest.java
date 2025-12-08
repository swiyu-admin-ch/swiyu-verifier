package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRejectionDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.callback.WebhookService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.PresentationSubmissionService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.PresentationVerificationService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.config.PresentationVerificationStrategyRegistry;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.DcqlEvaluator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.usecase.DcqlVerificationUseCase;
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
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.getPresentationSubmissionString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresentationVerificationServiceTest {

    private PresentationVerificationService presentationVerificationService;
    private WebhookService webhookService;

    private Management managementEntity;
    private UUID managementId;
    private PresentationVerifier<String> stringPresentationVerifier;
    private PresentationVerifier<SdJwt> sdJwtPresentationVerifier;
    private DcqlVerificationUseCase dcqlVerificationUseCase;
    private ObjectMapper objectMapper;
    private PresentationVerificationStrategyRegistry strategyRegistry; // Registry für Format-Strategien
    private PresentationSubmissionService submissionService;

    @BeforeEach
    void setUp() {
        ManagementRepository managementRepository = mock(ManagementRepository.class);
        objectMapper = new ObjectMapper();
        webhookService = mock(WebhookService.class);
        stringPresentationVerifier = mock(PresentationVerifier.class);
        sdJwtPresentationVerifier = mock(PresentationVerifier.class);
        dcqlVerificationUseCase = mock(DcqlVerificationUseCase.class);
        strategyRegistry = mock(PresentationVerificationStrategyRegistry.class);
        // provide a real Validator to the submissionService to avoid NPE
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        submissionService = new PresentationSubmissionService(objectMapper, validator);

        presentationVerificationService = new PresentationVerificationService(
                managementRepository,
                webhookService,
                dcqlVerificationUseCase,
                strategyRegistry,
                submissionService

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
        var vpToken = getVpToken();
        var mockRequest = getMockRequest(vpToken, getPresentationSubmissionString(UUID.randomUUID()));

        when(stringPresentationVerifier.verify(vpToken, managementEntity)).thenReturn("credential-data");
        // stub registry with a strategy delegating to stringPresentationVerifier
        when(strategyRegistry.getStrategy(anyString())).thenReturn(new ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerificationStrategy() {
            @Override
            public String verify(String vpToken, Management managementEntity, ch.admin.bj.swiyu.verifier.api.submission.PresentationSubmissionDto presentationSubmission) {
                return stringPresentationVerifier.verify(vpToken, managementEntity);
            }
            @Override
            public String getSupportedFormat() { return "vc+sd-jwt"; }
        });

        presentationVerificationService.receiveVerificationPresentation(managementId, mockRequest);

        verify(managementEntity).verificationSucceeded("credential-data");
        verify(webhookService).produceEvent(managementId);
    }

    @Test
    void receiveDcqlVerificationPresentation_thenSuccess() throws JsonProcessingException {
        var credentialRequestId = "TestIdRequest";
        var dcqlQuery = getDcqlQuery(credentialRequestId);
        var vpToken = getVpToken();
        var request = new VerificationPresentationDCQLRequestDto(Map.of(credentialRequestId, List.of(vpToken)));
        var sdJwt = mockVerifySdJwt(vpToken);
        when(managementEntity.getDcqlQuery()).thenReturn(dcqlQuery);
        when(sdJwtPresentationVerifier.verify(Mockito.anyString(), Mockito.eq(managementEntity))).thenReturn(sdJwt);

        var expectedVerificationSucceededData = objectMapper.writeValueAsString(Map.of(credentialRequestId, List.of(sdJwt.getClaims().getClaims())));

        when(dcqlVerificationUseCase.process(Mockito.eq(managementEntity), Mockito.eq(request))).thenReturn(expectedVerificationSucceededData);

        assertDoesNotThrow(() -> presentationVerificationService.receiveVerificationPresentationDCQL(managementId, request));
        verify(managementEntity).verificationSucceeded(expectedVerificationSucceededData);
        verify(webhookService).produceEvent(managementId);
    }

    @Test
    void receiveVerificationPresentation_clientRejected() {
        VerificationPresentationRejectionDto request = mock(VerificationPresentationRejectionDto.class);
        when(request.getErrorDescription()).thenReturn("User cancelled");

        presentationVerificationService.receiveVerificationPresentationClientRejection(managementId, request);

        verify(managementEntity).verificationFailedDueToClientRejection("User cancelled");
        verify(webhookService).produceEvent(managementId);
        verify(managementEntity, never()).verificationSucceeded(any());
    }

    @Test
    void receiveVerificationPresentation_processClosed() {
        when(managementEntity.isExpired()).thenReturn(true);
        VerificationPresentationRequestDto request = mock(VerificationPresentationRequestDto.class);

        assertThrows(ProcessClosedException.class, () ->
                presentationVerificationService.receiveVerificationPresentation(managementId, request));
        verify(webhookService).produceEvent(managementId);
    }

    @Test
    void receiveVerificationPresentation_verificationException() throws JsonProcessingException {
        var mockRequest = getMockRequestNoVpToken();

        var exception = assertThrows(VerificationException.class, () ->
                presentationVerificationService.receiveVerificationPresentation(managementId, mockRequest));

        assertEquals(AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM, exception.getErrorResponseCode());

        verify(managementEntity).verificationFailed(any(), any());
        verify(webhookService).produceEvent(managementId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"User declined the verification request", ""})
    @NullSource
    void receiveVerificationPresentationClientRejection_thenSuccess(String errorDescription) {
        VerificationPresentationRejectionDto rejectionRequest = mock(VerificationPresentationRejectionDto.class);
        when(rejectionRequest.getErrorDescription()).thenReturn(errorDescription);

        presentationVerificationService.receiveVerificationPresentationClientRejection(managementId, rejectionRequest);

        verify(managementEntity).verificationFailedDueToClientRejection(errorDescription);
        verify(webhookService).produceEvent(managementId);
        verify(managementEntity, never()).verificationSucceeded(any());
    }

    @Test
    void receiveVerificationPresentationClientRejection_processClosed_thenThrowsException() {
        when(managementEntity.isExpired()).thenReturn(true);
        VerificationPresentationRejectionDto rejectionRequest = mock(VerificationPresentationRejectionDto.class);
        when(rejectionRequest.getErrorDescription()).thenReturn("User cancelled");

        assertThrows(ProcessClosedException.class, () ->
                presentationVerificationService.receiveVerificationPresentationClientRejection(managementId, rejectionRequest));

        verify(webhookService).produceEvent(managementId);
        verify(managementEntity, never()).verificationFailedDueToClientRejection(any());
    }

    @Test
    void receiveVerificationPresentationClientRejection_verificationNotPending_thenThrowsException() {
        when(managementEntity.isVerificationPending()).thenReturn(false);
        VerificationPresentationRejectionDto rejectionRequest = mock(VerificationPresentationRejectionDto.class);
        when(rejectionRequest.getErrorDescription()).thenReturn("User cancelled");

        assertThrows(ProcessClosedException.class, () ->
                presentationVerificationService.receiveVerificationPresentationClientRejection(managementId, rejectionRequest));

        verify(webhookService).produceEvent(managementId);
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
    private DcqlQuery getDcqlQuery(String dcqlCredentialId) {
        var requestedCredential = new DcqlCredential(
                dcqlCredentialId,
                "dc+sd-jwt",
                new DcqlCredentialMeta(null, List.of(SDJWTCredentialMock.DEFAULT_VCT), null),
                List.of(
                        new DcqlClaim(null, List.of("birthdate"), null),
                        new DcqlClaim(null, List.of("last_name"), null)),
                true,
                false);

        return new DcqlQuery(List.of(requestedCredential), null);
    }
}