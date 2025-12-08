package ch.admin.bj.swiyu.verifier.oid4vp.usecase;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationError;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.DcqlEvaluator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.usecase.DcqlVerificationUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DcqlVerificationUseCaseTest {

    private PresentationVerifier<SdJwt> sdJwtPresentationVerifier;
    private DcqlEvaluator dcqlEvaluator;
    private ObjectMapper objectMapper;
    private DcqlVerificationUseCase useCase;

    @BeforeEach
    void setUp() {
        sdJwtPresentationVerifier = mock(PresentationVerifier.class);
        dcqlEvaluator = mock(DcqlEvaluator.class);
        objectMapper = new ObjectMapper();
        useCase = new DcqlVerificationUseCase(sdJwtPresentationVerifier, dcqlEvaluator, objectMapper);
    }

    @Test
    void process_happyPath_returnsJsonWithClaims() {
        // Arrange
        var management = mock(Management.class);
        var credentialId = "cred-1";
        var meta = new DcqlCredentialMeta(null, List.of("vct:test"), null);
        var claims = List.of(new DcqlClaim(null, List.of("given_name"), null));
        var requestedCredential = new DcqlCredential(credentialId, "dc+sd-jwt", meta, claims, true, false);
        var dcqlQuery = new DcqlQuery(List.of(requestedCredential), null);
        when(management.getDcqlQuery()).thenReturn(dcqlQuery);

        var vpToken = "vp-token-sdjwt";
        var request = new VerificationPresentationDCQLRequestDto(Map.of(credentialId, List.of(vpToken)));

        var sdJwt = mock(SdJwt.class);
        when(sdJwtPresentationVerifier.verify(vpToken, management)).thenReturn(sdJwt);
        when(dcqlEvaluator.filterByVct(anyList(), eq(meta))).thenReturn(List.of(sdJwt));
        // validateRequestedClaims should be called without throwing
        doNothing().when(dcqlEvaluator).validateRequestedClaims(eq(sdJwt), eq(claims));

        Map<String, Object> claimMap = Map.of("given_name", "Alice");
        // simulate extraction of claims from SdJwt via mocked JWTClaimsSet
        JWTClaimsSet claimsObj = mock(JWTClaimsSet.class);
        when(claimsObj.getClaims()).thenReturn(claimMap);
        when(sdJwt.getClaims()).thenReturn(claimsObj);

        // Act
        var resultJson = useCase.process(management, request);

        // Assert
        assertTrue(resultJson.contains("\"" + credentialId + "\""));
        assertTrue(resultJson.contains("\"given_name\":\"Alice\""));
        verify(sdJwtPresentationVerifier).verify(vpToken, management);
        verify(dcqlEvaluator).filterByVct(anyList(), eq(meta));
        verify(dcqlEvaluator).validateRequestedClaims(eq(sdJwt), eq(claims));
    }

    @Test
    void process_missingVpToken_throwsIllegalArgumentException() {
        // Arrange
        var management = mock(Management.class);
        var credentialId = "cred-1";
        var meta = new DcqlCredentialMeta(null, List.of("vct:test"), null);
        var requestedCredential = new DcqlCredential(credentialId, "dc+sd-jwt", meta, List.of(), true, false);
        var dcqlQuery = new DcqlQuery(List.of(requestedCredential), null);
        when(management.getDcqlQuery()).thenReturn(dcqlQuery);

        var request = new VerificationPresentationDCQLRequestDto(Map.of()); // missing token for cred-1

        // Act + Assert
        var ex = assertThrows(VerificationException.class, () -> useCase.process(management, request));
        assertEquals(ex.getErrorType(), VerificationError.INVALID_REQUEST);
    }
}
