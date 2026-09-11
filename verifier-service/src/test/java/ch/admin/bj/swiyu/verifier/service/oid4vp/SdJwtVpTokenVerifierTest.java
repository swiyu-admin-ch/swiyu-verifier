package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwt;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwtVcValidator;
import ch.admin.bj.swiyu.sdjwtverifier.exception.SdJwtVerificationException;
import ch.admin.bj.swiyu.statuslist.TokenStatusListVerifier;
import ch.admin.bj.swiyu.statuslist.dto.StatusVerificationResultDto;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListTokenDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.IssuerTrustMarker;
import ch.admin.bj.swiyu.verifier.domain.TrustMethod;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListCacheService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.HOLDER_BINDING_MISMATCH;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.UNRESOLVABLE_STATUS_LIST;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_KID_HEADER_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SdJwtVpTokenVerifier} focusing on trust evaluation and holder binding audience checks.
 */
class SdJwtVpTokenVerifierTest {

    private static final String TEST_NONCE = "test-nonce";

    private DidResolverFacade issuerPublicKeyLoader;
    private StatusListCacheService statusListResolver;
    private TokenStatusListVerifier statusListVerifier;
    private Management management;

    private SdJwtVpTokenVerifier verifier;
    private final String prefix = "prefix";
    private final String clientId = "did:example:verifier";

    @BeforeEach
    void setUp() {
        issuerPublicKeyLoader = mock(DidResolverFacade.class);
        statusListResolver = mock(StatusListCacheService.class);
        DidJwtValidator didJwtValidator = mock(DidJwtValidator.class);
        statusListVerifier = mock(TokenStatusListVerifier.class);
        ApplicationProperties applicationProperties = mock(ApplicationProperties.class);
        VerificationProperties verificationProperties = mock(VerificationProperties.class);
        IssuerTrustValidator issuerTrustValidator = mock(IssuerTrustValidator.class);
        management = mock(Management.class);

        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);
        when(applicationProperties.getClientId()).thenReturn(clientId);
        when(applicationProperties.getClientIdPrefix()).thenReturn(prefix);
        when(management.getId()).thenReturn(UUID.randomUUID());
        when(management.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(management.getTrustAnchors()).thenReturn(List.of());
        when(management.getRequestNonce()).thenReturn(TEST_NONCE);
        when(management.getConfigurationOverride()).thenReturn(new ConfigurationOverride(null, null, null, null, null, null));
        when(issuerTrustValidator.validateTrust(anyString(), anyString(), eq(management))).thenReturn(new IssuerTrustMarker(TrustMethod.TRUST_PROTOCOL_1_0, false, false, false, false, false));
        when(issuerPublicKeyLoader.resolveKey(DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicJWK());

        verifier = new SdJwtVpTokenVerifier(issuerPublicKeyLoader, didJwtValidator, statusListResolver, applicationProperties, verificationProperties, statusListVerifier);
    }

    @Deprecated(since = "Trust Protocol 2.0")
    @Test
    void verifyVpToken_Legacy_whenTrustAnchorCanIssue_thenSucceeds() throws JOSEException, JacksonException, NoSuchAlgorithmException, ParseException {
        // Arrange: VC issued by third party, not directly trusted via acceptedIssuerDids
        var vcIssuerDid = "did:webvh:scid:third";
        var vcIssuerKid = vcIssuerDid + "#key-1";
        when(issuerPublicKeyLoader.resolveKey(vcIssuerKid))
                .thenReturn(KeyFixtures.issuerKey().toPublicJWK());

        var emulator = new SDJWTCredentialMock(vcIssuerDid, vcIssuerKid);
        var sdjwt = emulator.createSDJWTMock();
        var vpTokenString = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, prefix + ":" + clientId);

        // Trust Statement: separate trust anchor vouches that vcIssuerDid canIssue DEFAULT_VCT
        var trustRegistryUrl = "https://trust-registry.example.com";
        var trustIssuerDid = "did:webvh:scid:trust";
        var trustIssuerKid = trustIssuerDid + "#key-1";
        when(issuerPublicKeyLoader.resolveKey(trustIssuerKid))
                .thenReturn(KeyFixtures.issuerKey().toPublicJWK());

        // Important: subject of trust statement must match vcIssuerDid so that isProvidingTrust() returns true
        var trustStatement = emulator.createTrustStatementIssuanceV1(trustIssuerDid, trustIssuerKid, vcIssuerDid);
        when(management.getTrustAnchors())
                .thenReturn(List.of(new TrustAnchor(trustIssuerDid, trustRegistryUrl)));
        when(issuerPublicKeyLoader.resolveTrustStatement(trustRegistryUrl, SDJWTCredentialMock.DEFAULT_VCT))
                .thenReturn(trustStatement);

        // Act
        SdJwt verified = verifier.verifyVpTokenTrustStatement(vpTokenString, management);

        // Assert
        // TODO: It should verify that the trust evaluation logic correctly accepted the credential based on the trust
        //  statement, for example by asserting specific claims or verifying that no exception was thrown due to trust issues.
        assertThat(verified.getClaims()).isNotNull();
        assertThat(verified.getHeader()).isNotNull();
    }

    /**
     * Test where the issuer tries to provide his own trust statement
     */
    @Deprecated(since = "Trust Protocol 2.0")
    @Test
    void verifyVpToken_Legacy_whenTrustIssuerMismatch_thenFailure() throws Exception {
        var vcIssuerDid = "did:webvh:scid:issuer";
        var vcIssuerKid = vcIssuerDid + "#key-1";

        var emulator = new SDJWTCredentialMock(vcIssuerDid, vcIssuerKid);

        // Trust Statement: separate trust anchor vouches that vcIssuerDid canIssue DEFAULT_VCT
        var trustIssuerDid = "did:webvh:scid:trust";
        // The Trust Statement is not a real trust statement, but signed by the malicious issuer
        var trustIssuerKid = vcIssuerDid + "#key-1";

        // Important: subject of trust statement must match vcIssuerDid so that isProvidingTrust() returns true
        var trustStatement = emulator.createTrustStatementIssuanceV1(trustIssuerDid, trustIssuerKid, vcIssuerDid);

        // Act
        assertThrows(VerificationException.class, () ->  verifier.verifyVpTokenTrustStatement(trustStatement, management));
    }

    @Test
    void validateKeyBinding_whenAudienceMismatch_thenHolderBindingMismatch() throws JOSEException, NoSuchAlgorithmException, ParseException {
        // Arrange: valid SD-JWT with key binding, but audience is not our clientId
        var emulator = new SDJWTCredentialMock(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE);
        var sdjwt = emulator.createSDJWTMock();

        when(issuerPublicKeyLoader.resolveKey(DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicJWK());

        // Audience intentionally mismatched
        var wrongAudience = "did:example:someone-else";
        var vpTokenString = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, wrongAudience);

        // Act & Assert
        VerificationException ex = assertThrows(VerificationException.class, () -> verifier.verifyVpTokenTrustStatement(vpTokenString, management));
        assertEquals(HOLDER_BINDING_MISMATCH, ex.getErrorResponseCode());
    }

    @Test
    void validateKeyBinding_whenHolderBindingNotRequiredAndMissing_thenSkipsValidator() throws SdJwtVerificationException {
        SdJwt sdJwt = mock(SdJwt.class);
        SdJwtVcValidator validator = mock(SdJwtVcValidator.class);

        when(sdJwt.hasKeyBinding()).thenReturn(false);

        verifier.validateKeyBinding(sdJwt, false, management, validator);

        verify(validator, never()).validateKeyBinding(sdJwt, prefix + ":" + clientId, TEST_NONCE, 120);
    }

    @Test
    void validateKeyBinding_whenHolderBindingRequiredAndMissing_thenThrowsHolderBindingMismatch() {
        SdJwt sdJwt = mock(SdJwt.class);
        SdJwtVcValidator validator = mock(SdJwtVcValidator.class);

        when(sdJwt.hasKeyBinding()).thenReturn(false);

        VerificationException ex = assertThrows(VerificationException.class,
                () -> verifier.validateKeyBinding(sdJwt, true, management, validator));

        assertEquals(HOLDER_BINDING_MISMATCH, ex.getErrorResponseCode());
    }

    @Test
    void validateKeyBinding_whenHolderBindingPresent_thenDelegatesWithExpectedAudienceAndNonce() throws SdJwtVerificationException {
        SdJwt sdJwt = mock(SdJwt.class);
        SdJwtVcValidator validator = mock(SdJwtVcValidator.class);

        when(sdJwt.hasKeyBinding()).thenReturn(true);

        verifier.validateKeyBinding(sdJwt, true, management, validator);

        verify(validator).validateKeyBinding(sdJwt, prefix + ":" + clientId, TEST_NONCE, 120);
    }

    @Test
    void validateKeyBinding_whenValidatorRejectsProof_thenThrowsHolderBindingMismatch() throws SdJwtVerificationException {
        SdJwt sdJwt = mock(SdJwt.class);
        SdJwtVcValidator validator = mock(SdJwtVcValidator.class);

        when(sdJwt.hasKeyBinding()).thenReturn(true);
        doThrow(new SdJwtVerificationException("invalid proof"))
                .when(validator)
                .validateKeyBinding(sdJwt, prefix + ":" + clientId, TEST_NONCE, 120);

        VerificationException ex = assertThrows(VerificationException.class,
                () -> verifier.validateKeyBinding(sdJwt, true, management, validator));

        assertEquals(HOLDER_BINDING_MISMATCH, ex.getErrorResponseCode());
    }

    @Test
    void canHaveKeyBinding_whenCnfClaimPresent_thenReturnsTrue() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().claim("cnf", Map.of("kid", "test")).build();

        assertTrue(verifier.canHaveKeyBinding(claims));
    }

    @Test
    void canHaveKeyBinding_whenCnfClaimMissing_thenReturnsFalse() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(DEFAULT_ISSUER_ID).build();

        assertFalse(verifier.canHaveKeyBinding(claims));
    }

    @Test
    void verifyStatus_whenNoStatusClaimPresent_thenReturnsEmpty() {
        Map<String, Object> claims = new HashMap<>();

        assertThat(verifier.verifyStatus(claims, new JWSHeader.Builder(JWSAlgorithm.ES256).build())).isEmpty();
    }

    @Test
    void verifyStatus_whenStatusListExists_thenReturnsVerificationResult() throws Exception {
        Map<String, Object> claims = Map.of(
                "status", Map.of(
                        "status_list", Map.of(
                                "idx", 1,
                                "uri", "https://example.com/status/1"
                        )
                )
        );
        TokenStatusListTokenDto statusListToken = mock(TokenStatusListTokenDto.class);
        StatusVerificationResultDto verificationResult = mock(StatusVerificationResultDto.class);

        when(statusListResolver.getTokenStatusListTokenByUri("https://example.com/status/1")).thenReturn(statusListToken);
        when(statusListVerifier.verifyStatus(any(), eq(statusListToken))).thenReturn(verificationResult);

        assertThat(verifier.verifyStatus(claims, new JWSHeader.Builder(JWSAlgorithm.ES256).build()))
                .contains(verificationResult);
    }

    @Test
    void verifyStatus_whenStatusListCannotBeResolved_thenThrowsUnresolvableStatusList() {
        Map<String, Object> claims = Map.of(
                "status", Map.of(
                        "status_list", Map.of(
                                "idx", 1,
                                "uri", "https://example.com/status/1"
                        )
                )
        );

        when(statusListResolver.getTokenStatusListTokenByUri("https://example.com/status/1")).thenReturn(null);

        VerificationException ex = assertThrows(VerificationException.class,
                () -> verifier.verifyStatus(claims, new JWSHeader.Builder(JWSAlgorithm.ES256).build()));

        assertEquals(UNRESOLVABLE_STATUS_LIST, ex.getErrorResponseCode());
    }
}