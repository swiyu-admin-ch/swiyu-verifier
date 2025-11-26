package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRejectionDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.callback.WebhookService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.SdJwtVerificationService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.VerificationService;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.getPresentationSubmissionString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificationServiceTest {

    private VerificationService verificationService;
    private WebhookService webhookService;

    private Management managementEntity;
    private UUID managementId;
    private SdJwtVerificationService sdJwtVerificationService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        VerificationProperties verificationProperties = mock(VerificationProperties.class);
        ApplicationProperties applicationProperties = mock(ApplicationProperties.class);
        ManagementRepository managementRepository = mock(ManagementRepository.class);
        IssuerPublicKeyLoader issuerPublicKeyLoader = mock(IssuerPublicKeyLoader.class);
        StatusListReferenceFactory statusListReferenceFactory = mock(StatusListReferenceFactory.class);
        objectMapper = new ObjectMapper();
        webhookService = mock(WebhookService.class);
        sdJwtVerificationService = mock(SdJwtVerificationService.class);

        verificationService = new VerificationService(
                verificationProperties,
                applicationProperties,
                managementRepository,
                issuerPublicKeyLoader,
                statusListReferenceFactory,
                objectMapper,
                webhookService,
                sdJwtVerificationService
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

        var mockRequest = getMockRequest(getVpToken(), getPresentationSubmissionString(UUID.randomUUID()));

        try (MockedConstruction<SdjwtCredentialVerifier> mocked = mockConstruction(SdjwtCredentialVerifier.class,
                (mock, context) -> when(mock.verifyPresentation()).thenReturn("credential-data"))) {
            verificationService.receiveVerificationPresentation(managementId, mockRequest);

            verify(managementEntity).verificationSucceeded("credential-data");
            verify(webhookService).produceEvent(managementId);
        }
    }

    @Test
    void receiveDcqlVerificationPresentation_withoutHolderRequestedHolderBinding_thenSuccess() throws JsonProcessingException {
        var credentialRequestId = "TestIdRequest";
        var dcqlQuery = getDcqlQuery(credentialRequestId, false);
        var vpToken = getVpToken();
        var request = new VerificationPresentationDCQLRequestDto(Map.of(credentialRequestId, List.of(vpToken)));
        var sdJwt = mockVerifySdJwt(vpToken);
        when(managementEntity.getDcqlQuery()).thenReturn(dcqlQuery);
        when(sdJwtVerificationService.verifyVpTokenForDCQLRequest(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(sdJwt);

        assertDoesNotThrow(() -> verificationService.receiveVerificationPresentationDCQL(managementId, request));
        var expectedVerificationSucceededData = objectMapper.writeValueAsString(Map.of(credentialRequestId, List.of(sdJwt.getClaims().getClaims())));
        verify(managementEntity).verificationSucceeded(expectedVerificationSucceededData);
        verify(webhookService).produceEvent(managementId);
    }

    @Test
    void receiveVerificationPresentation_clientRejected() {
        VerificationPresentationRejectionDto request = mock(VerificationPresentationRejectionDto.class);
        when(request.getErrorDescription()).thenReturn("User cancelled");

        verificationService.receiveVerificationPresentationClientRejection(managementId, request);

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
        var mockRequest = getMockRequestNoVpToken();

        var exception = assertThrows(VerificationException.class, () ->
                verificationService.receiveVerificationPresentation(managementId, mockRequest));

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

        verificationService.receiveVerificationPresentationClientRejection(managementId, rejectionRequest);

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
                verificationService.receiveVerificationPresentationClientRejection(managementId, rejectionRequest));

        verify(webhookService).produceEvent(managementId);
        verify(managementEntity, never()).verificationFailedDueToClientRejection(any());
    }

    @Test
    void receiveVerificationPresentationClientRejection_verificationNotPending_thenThrowsException() {
        when(managementEntity.isVerificationPending()).thenReturn(false);
        VerificationPresentationRejectionDto rejectionRequest = mock(VerificationPresentationRejectionDto.class);
        when(rejectionRequest.getErrorDescription()).thenReturn("User cancelled");

        assertThrows(ProcessClosedException.class, () ->
                verificationService.receiveVerificationPresentationClientRejection(managementId, rejectionRequest));

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