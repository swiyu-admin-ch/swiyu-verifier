package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.oid4vp.VpTokenVerifier;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.List;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.HOLDER_BINDING_MISMATCH;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.ISSUER_NOT_ACCEPTED;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_KID_HEADER_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VpTokenVerifier} focusing on trust evaluation and holder binding audience checks.
 */
class VpTokenVerifierTest {

    private static final String TEST_NONCE = "test-nonce";

    private IssuerPublicKeyLoader issuerPublicKeyLoader;
    private StatusListReferenceFactory statusListReferenceFactory;
    private ApplicationProperties applicationProperties;
    private VerificationProperties verificationProperties;
    private Management management;

    private VpTokenVerifier verifier;

    @BeforeEach
    void setUp() throws LoadingPublicKeyOfIssuerFailedException, JOSEException {
        issuerPublicKeyLoader = mock(IssuerPublicKeyLoader.class);
        statusListReferenceFactory = mock(StatusListReferenceFactory.class);
        applicationProperties = mock(ApplicationProperties.class);
        verificationProperties = mock(VerificationProperties.class);
        management = mock(Management.class);

        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);
        when(applicationProperties.getClientId()).thenReturn("did:example:verifier");
        when(management.getId()).thenReturn(UUID.randomUUID());
        when(management.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(management.getTrustAnchors()).thenReturn(List.of());
        when(management.getRequestNonce()).thenReturn(TEST_NONCE);
        when(management.getConfigurationOverride()).thenReturn(new ConfigurationOverride(null, null, null, null, null));

        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());

        // Status list verification is out of scope of this unit, so we simulate "no status entries"
        when(statusListReferenceFactory.createStatusListReferences(any(), any())).thenReturn(List.of());

        verifier = new VpTokenVerifier(issuerPublicKeyLoader, statusListReferenceFactory, applicationProperties, verificationProperties);
    }

    @Test
    void verifyVpToken_whenTrustAnchorCanIssue_thenSucceeds() throws JOSEException, JsonProcessingException, LoadingPublicKeyOfIssuerFailedException, NoSuchAlgorithmException, ParseException {
        // Arrange: VC issued by third party, not directly trusted via acceptedIssuerDids
        var vcIssuerDid = "did:example:third";
        var vcIssuerKid = vcIssuerDid + "#key-1";
        when(issuerPublicKeyLoader.loadPublicKey(vcIssuerDid, vcIssuerKid))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());

        var emulator = new SDJWTCredentialMock(vcIssuerDid, vcIssuerKid);
        var sdjwt = emulator.createSDJWTMock();
        var vpTokenString = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, applicationProperties.getClientId());
        var sdJwt = new SdJwt(vpTokenString);

        // Trust Statement: separate trust anchor vouches that vcIssuerDid canIssue DEFAULT_VCT
        var trustRegistryUrl = "https://trust-registry.example.com";
        var trustIssuerDid = "did:example:trust";
        var trustIssuerKid = trustIssuerDid + "#key-1";
        when(issuerPublicKeyLoader.loadPublicKey(trustIssuerDid, trustIssuerKid))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());

        // Important: subject of trust statement must match vcIssuerDid so that isProvidingTrust() returns true
        var trustStatement = emulator.createTrustStatementIssuanceV1(trustIssuerDid, trustIssuerKid, vcIssuerDid);
        when(management.getTrustAnchors())
                .thenReturn(List.of(new TrustAnchor(trustIssuerDid, trustRegistryUrl)));
        when(issuerPublicKeyLoader.loadTrustStatement(trustRegistryUrl, SDJWTCredentialMock.DEFAULT_VCT))
                .thenReturn(List.of(trustStatement));

        // Act
        SdJwt verified = verifier.verifyVpToken(sdJwt, management);

        // Assert
        // TODO: It should verify that the trust evaluation logic correctly accepted the credential based on the trust
        //  statement, for example by asserting specific claims or verifying that no exception was thrown due to trust issues.
        assertThat(verified.getClaims()).isNotNull();
        assertThat(verified.getHeader()).isNotNull();
    }

    @Test
    void validateKeyBinding_whenAudienceMismatch_thenHolderBindingMismatch() throws JOSEException, LoadingPublicKeyOfIssuerFailedException, NoSuchAlgorithmException, ParseException {
        // Arrange: valid SD-JWT with key binding, but audience is not our clientId
        var vcIssuerDid = DEFAULT_ISSUER_ID;
        var vcIssuerKid = DEFAULT_KID_HEADER_VALUE;
        var emulator = new SDJWTCredentialMock(vcIssuerDid, vcIssuerKid);
        var sdjwt = emulator.createSDJWTMock();

        when(issuerPublicKeyLoader.loadPublicKey(vcIssuerDid, vcIssuerKid))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());

        // Audience intentionally mismatched
        var wrongAudience = "did:example:someone-else";
        var vpTokenString = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, wrongAudience);
        var sdJwt = new SdJwt(vpTokenString);

        // Act & Assert
        VerificationException ex = assertThrows(VerificationException.class, () -> verifier.verifyVpToken(sdJwt, management));
        assertEquals(HOLDER_BINDING_MISMATCH, ex.getErrorResponseCode());
    }
}
