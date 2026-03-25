package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import com.authlete.sd.Disclosure;
import com.authlete.sd.SDJWT;
import com.authlete.sd.SDObjectBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.*;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.HOLDER_BINDING_MISMATCH;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SdJwtVpTokenVerifier} focusing on trust evaluation and holder binding audience checks.
 */
class SdJwtVpTokenVerifierTest {

    private static final String TEST_NONCE = "test-nonce";

    private IssuerPublicKeyLoader issuerPublicKeyLoader;
    private StatusListReferenceFactory statusListReferenceFactory;
    private ApplicationProperties applicationProperties;
    private VerificationProperties verificationProperties;
    private Management management;

    private SdJwtVpTokenVerifier verifier;

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

        verifier = new SdJwtVpTokenVerifier(issuerPublicKeyLoader, statusListReferenceFactory, applicationProperties, verificationProperties);
    }

    @Test
    void verifyVpToken_Legacy_whenTrustAnchorCanIssue_thenSucceeds() throws JOSEException, JsonProcessingException, LoadingPublicKeyOfIssuerFailedException, NoSuchAlgorithmException, ParseException {
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
        SdJwt verified = verifier.verifyVpTokenTrustStatement(sdJwt, management);

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
        VerificationException ex = assertThrows(VerificationException.class, () -> verifier.verifyVpTokenTrustStatement(sdJwt, management));
        assertEquals(HOLDER_BINDING_MISMATCH, ex.getErrorResponseCode());
    }

    @Test
    void processDisclosures_whenDisclosureClaimNameCollides_thenMalformedCredential() {
        // Arrange: create a Disclosure whose claimName already exists at the level of the _sd key
        var salt = "salt-1";
        var claimName = "name";
        var claimValue = "Bob";

        Disclosure disclosure = new Disclosure(salt, claimName, claimValue);
        String digest = disclosure.digest();

        // Build a claim object that has an _sd array containing the disclosure digest and also an existing claim with the same name
        Map<String, Object> credentialSubject = new HashMap<>();
        credentialSubject.put("_sd", List.of(digest));
        credentialSubject.put(claimName, "Alice"); // existing field that collides with disclosure claimName

        JWTClaimsSet claimSet = new JWTClaimsSet.Builder()
                .claim("credentialSubject", credentialSubject)
                .build();

        SdJwtVpTokenVerifier verifier = new SdJwtVpTokenVerifier(issuerPublicKeyLoader, statusListReferenceFactory, applicationProperties, verificationProperties);

        var ex = assertThrows(VerificationException.class, () -> verifier.processDisclosures(claimSet, List.of(disclosure), UUID.randomUUID()));

        assertThat(ex.getErrorResponseCode())
                .as("Should throw malformed credential error when disclosure claim name collides with existing claim")
                .isEqualTo(VerificationErrorResponseCode.MALFORMED_CREDENTIAL);

        assertThat(ex.getErrorDescription())
                .as("Should throw understandable error message indicating the claim name collision")
                .isEqualTo("Claim name already exists at the level of the _sd key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_sd", "..."})
    void processDisclosures_whenInvalidDisclosure_thenMalformedCredential(String invalidInput) {
        // Arrange: create a Disclosure whose claimName already exists at the level of the _sd key
        var salt = "salt-1";
        var claimValue = List.of();

        Disclosure disclosure = new Disclosure(salt, invalidInput, claimValue);
        String digest = disclosure.digest();

        // Build a claim object that has an _sd array containing the disclosure digest and also an existing claim with the same name
        Map<String, Object> credentialSubject = new HashMap<>();
        credentialSubject.put("_sd", List.of(digest));

        JWTClaimsSet claimSet = new JWTClaimsSet.Builder()
                .claim("credentialSubject", credentialSubject)
                .build();

        SdJwtVpTokenVerifier verifier = new SdJwtVpTokenVerifier(issuerPublicKeyLoader, statusListReferenceFactory, applicationProperties, verificationProperties);

        var ex = assertThrows(VerificationException.class, () -> verifier.processDisclosures(claimSet, List.of(disclosure), UUID.randomUUID()));

        assertThat(ex.getErrorResponseCode())
                .as("Should throw malformed credential error when disclosure claim name collides with existing claim")
                .isEqualTo(VerificationErrorResponseCode.MALFORMED_CREDENTIAL);

        assertThat(ex.getErrorDescription())
                .as("Should throw understandable error message indicating the claim name collision")
                .isEqualTo("Illegal disclosure found with name _sd or ...");
    }

    @Test
    void processDisclosures_whenDeeplyNested_thenSuccess() throws ParseException {

        SDObjectBuilder builder = new SDObjectBuilder();
        List<Disclosure> disclosure = new ArrayList<>();

        var claimsForSdJWT = getClaimsFromSdJwt(disclosure);

        JWTClaimsSet claimsSet = JWTClaimsSet.parse(claimsForSdJWT.build());
        SdJwtVpTokenVerifier verifier = new SdJwtVpTokenVerifier(issuerPublicKeyLoader, statusListReferenceFactory, applicationProperties, verificationProperties);
        assertDoesNotThrow(() -> verifier.processDisclosures(claimsSet, disclosure, UUID.randomUUID()));
    }

    @Test
    void validateDisclosures_whenDeeplyNested_thenSuccess() throws ParseException, JOSEException {

        List<Disclosure> disclosure = new ArrayList<>();

        var mgmtEntity = Management.builder()
                .id(UUID.randomUUID())
                .acceptedIssuerDids(List.of(DEFAULT_ISSUER_ID))
                .trustAnchors(List.of())
                .requestNonce(TEST_NONCE)
                .configurationOverride(new ConfigurationOverride(null, null, null, null, null))
                .build();

        var claimsForSdJWT = getClaimsFromSdJwt(disclosure);

        JWSHeader header =
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(new JOSEObjectType("vc+sd-jwt")).build();

        JWTClaimsSet claimsSet = JWTClaimsSet.parse(claimsForSdJWT.build());
        SignedJWT jwt = new SignedJWT(header, claimsSet);
        ECKey privateKey = new ECKeyGenerator(Curve.P_256).generate();
        JWSSigner signer = new ECDSASigner(privateKey);
        jwt.sign(signer);

        SDJWT sdJwt = new SDJWT(jwt.serialize(), disclosure);
        SdJwt sdjwt = new SdJwt(sdJwt.toString());
        sdjwt.setClaims(claimsSet);

        SdJwtVpTokenVerifier verifier = new SdJwtVpTokenVerifier(issuerPublicKeyLoader, statusListReferenceFactory, applicationProperties, verificationProperties);
        assertDoesNotThrow(() -> verifier.validateDisclosures(sdjwt, mgmtEntity));
    }
}