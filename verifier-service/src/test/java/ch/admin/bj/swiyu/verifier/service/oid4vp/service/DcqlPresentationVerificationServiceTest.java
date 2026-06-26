package ch.admin.bj.swiyu.verifier.service.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationError;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.DcqlEvaluator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.DcqlPresentationVerificationService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DcqlPresentationVerificationServiceTest {

    private PresentationVerifier sdJwtLegacyPresentationVerifier;
    private DcqlEvaluator dcqlEvaluator;
    private DcqlPresentationVerificationService dcqlPresentationVerificationService;
    private ApplicationProperties applicationProperties;

    @BeforeEach
    void setUp() {
        sdJwtLegacyPresentationVerifier = mock(PresentationVerifier.class);
        dcqlEvaluator = mock(DcqlEvaluator.class);
        ObjectMapper objectMapper = new ObjectMapper();
        applicationProperties = mock(ApplicationProperties.class);

        when(applicationProperties.getMaxVcsAccepted()).thenReturn(2);
        dcqlPresentationVerificationService = new DcqlPresentationVerificationService(sdJwtLegacyPresentationVerifier, dcqlEvaluator, objectMapper, applicationProperties);
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
        when(sdJwtLegacyPresentationVerifier.verify(vpToken, management,requestedCredential)).thenReturn(sdJwt);
        when(dcqlEvaluator.filterByVct(anyList(), eq(meta))).thenReturn(List.of(sdJwt));
        // validateRequestedClaims should be called without throwing
        doNothing().when(dcqlEvaluator).validateRequestedClaims(eq(sdJwt), eq(claims));

        Map<String, Object> claimMap = Map.of("given_name", "Alice");
        // simulate extraction of claims from SdJwt via mocked JWTClaimsSet
        JWTClaimsSet claimsObj = mock(JWTClaimsSet.class);
        when(claimsObj.getClaims()).thenReturn(claimMap);
        when(sdJwt.getClaims()).thenReturn(claimsObj);

        // Act
        var resultJson = dcqlPresentationVerificationService.process(management, request);

        // Assert
        assertTrue(resultJson.contains("\"" + credentialId + "\""));
        assertTrue(resultJson.contains("\"given_name\":\"Alice\""));
        verify(sdJwtLegacyPresentationVerifier).verify(vpToken, management,requestedCredential);
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
        var ex = assertThrows(VerificationException.class, () -> dcqlPresentationVerificationService.process(management, request));
        assertEquals(VerificationError.INVALID_REQUEST, ex.getErrorType());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false})
    void process_whenNotMultiple_throwsVerificationException(Boolean multiple) {
        // Arrange
        var management = mock(Management.class);
        var credentialId = "cred-1";
        var meta = new DcqlCredentialMeta(null, List.of("vct:test"), null);
        var claims = List.of(new DcqlClaim(null, List.of("given_name"), null));
        var requestedCredential = new DcqlCredential(credentialId, "dc+sd-jwt", meta, claims, true, multiple);
        var dcqlQuery = new DcqlQuery(List.of(requestedCredential), null);
        when(management.getDcqlQuery()).thenReturn(dcqlQuery);
        var vpToken = "vp-token-sdjwt";
        // Create Presentation with 2 credentials being presented
        var request = new VerificationPresentationDCQLRequestDto(Map.of(credentialId, List.of(vpToken, vpToken)));
        var exp = assertThrows(VerificationException.class, () -> dcqlPresentationVerificationService.process(management, request));
        assertEquals("Expected only 1 vp token for cred-1", exp.getErrorDescription());
    }

    @Test
    void process_whenTooManyInMultiple_throwsVerificationException() {
        // Arrange
        var management = mock(Management.class);
        var credentialId = "cred-1";
        var meta = new DcqlCredentialMeta(null, List.of("vct:test"), null);
        var claims = List.of(new DcqlClaim(null, List.of("given_name"), null));
        var requestedCredential = new DcqlCredential(credentialId, "dc+sd-jwt", meta, claims, true, true);
        var dcqlQuery = new DcqlQuery(List.of(requestedCredential), null);
        when(management.getDcqlQuery()).thenReturn(dcqlQuery);
        var vpToken = "vp-token-sdjwt";
        // Create Presentation with 2 credentials being presented
        List<String> vpTokens = new ArrayList<>();
        for (int i = 0; i < applicationProperties.getMaxVcsAccepted() + 1; i++) {
            vpTokens.add(vpToken);
        }
        var request = new VerificationPresentationDCQLRequestDto(Map.of(credentialId, vpTokens));
        var exp = assertThrows(VerificationException.class, () -> dcqlPresentationVerificationService.process(management, request));
        assertEquals("Cannot Accept more than 2 vcs received 3", exp.getErrorDescription());
    }
}
