package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.statuslist.TokenStatusListVerifier;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListCacheService;
import com.authlete.sd.Disclosure;
import com.authlete.sd.SDJWT;
import com.authlete.sd.SDObjectBuilder;
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
import tools.jackson.core.JacksonException;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.*;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.HOLDER_BINDING_MISMATCH;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SdJwtVpTokenVerifier} focusing on trust evaluation and holder binding audience checks.
 */
class SdJwtVpTokenVerifierTest {

    private static final String TEST_NONCE = "test-nonce";

    private DidResolverFacade issuerPublicKeyLoader;
    private Management management;

    private SdJwtVpTokenVerifier verifier;
    private final String prefix = "prefix";
    private final String clientId = "did:example:verifier";

    @BeforeEach
    void setUp() {
        issuerPublicKeyLoader = mock(DidResolverFacade.class);
        StatusListCacheService statusListResolver = mock(StatusListCacheService.class);
        DidJwtValidator didJwtValidator = mock(DidJwtValidator.class);
        TokenStatusListVerifier statusListVerifier = mock(TokenStatusListVerifier.class);
        ApplicationProperties applicationProperties = mock(ApplicationProperties.class);
        VerificationProperties verificationProperties = mock(VerificationProperties.class);
        management = mock(Management.class);

        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);
        when(applicationProperties.getClientId()).thenReturn(clientId);
        when(applicationProperties.getClientIdPrefix()).thenReturn(prefix);
        when(management.getId()).thenReturn(UUID.randomUUID());
        when(management.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(management.getTrustAnchors()).thenReturn(List.of());
        when(management.getRequestNonce()).thenReturn(TEST_NONCE);
        when(management.getConfigurationOverride()).thenReturn(new ConfigurationOverride(null, null, null, null, null, null));

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
        var sdJwt = new SdJwt(vpTokenString);

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
        SdJwt verified = verifier.verifyVpTokenTrustStatement(sdJwt, management);

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
        var vcIssuerDid = "did:webvh:scid:third";
        var vcIssuerKid = vcIssuerDid + "#key-1";

        var emulator = new SDJWTCredentialMock(vcIssuerDid, vcIssuerKid);

        // Trust Statement: separate trust anchor vouches that vcIssuerDid canIssue DEFAULT_VCT
        var trustIssuerDid = "did:webvh:scid:trust";
        // The Trust Statement is not a real trust statement, but signed by the malicious issuer
        var trustIssuerKid = vcIssuerDid + "#key-1";

        // Important: subject of trust statement must match vcIssuerDid so that isProvidingTrust() returns true
        var trustStatement = emulator.createTrustStatementIssuanceV1(trustIssuerDid, trustIssuerKid, vcIssuerDid);
        var sdJwt = new SdJwt(trustStatement);
        
        // Act
        assertThrows(VerificationException.class, () ->  verifier.verifyVpTokenTrustStatement(sdJwt, management));
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

        List<Disclosure> disclosure = new ArrayList<>();

        var claimsForSdJWT = getClaimsFromSdJwt(disclosure);

        JWTClaimsSet claimsSet = JWTClaimsSet.parse(claimsForSdJWT.build());
        assertDoesNotThrow(() -> verifier.processDisclosures(claimsSet, disclosure, UUID.randomUUID()));
    }

    @Test
    void processDisclosures_whitIncorrectSdAlg_thenError() throws ParseException, JOSEException {

        List<Disclosure> disclosure = new ArrayList<>();

        SDObjectBuilder builder = new SDObjectBuilder("sha-512");

        var nameDisc = new Disclosure("name", "Max Muster");
        builder.putSDClaim(nameDisc);
        disclosure.add(nameDisc);

        JWSHeader header =
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(new JOSEObjectType("dc+sd-jwt")).build();

        JWTClaimsSet claimsSet = JWTClaimsSet.parse(builder.build(true));
        SignedJWT jwt = new SignedJWT(header, claimsSet);
        ECKey privateKey = new ECKeyGenerator(Curve.P_256).generate();
        JWSSigner signer = new ECDSASigner(privateKey);
        jwt.sign(signer);

        SDJWT sdJwt = new SDJWT(jwt.serialize(), disclosure);
        SdJwt sdjwt = new SdJwt(sdJwt.toString());
        sdjwt.setClaims(claimsSet);

        var test = assertThrows(VerificationException.class, () -> verifier.validateDisclosures(sdjwt, management));
        assertThat(test.getErrorDescription()).as("Should throw understandable error message indicating the unsupported algorithm").isEqualTo("Unsupported _sd_alg value: sha-512");
    }

    @Test
    void processDisclosuresRecursive_withDuplicatedDigest_thenError() throws ParseException {

        List<Disclosure> disclosure = new ArrayList<>();

        var claimsForSdJWT = getClaimsFromWithDuplicatedDigestsSdJwt(disclosure);

        JWTClaimsSet claimsSet = JWTClaimsSet.parse(claimsForSdJWT.build());

        var ex = assertThrows(VerificationException.class, () -> verifier.processDisclosures(claimsSet, disclosure, UUID.randomUUID()));
        assertThat(ex.getErrorResponseCode())
                .as("Should throw malformed credential error when disclosure claim name collides with existing claim")
                .isEqualTo(VerificationErrorResponseCode.MALFORMED_CREDENTIAL);
        assertThat(ex.getErrorDescription())
                .as("Should throw understandable error message indicating the claim name collision")
                .startsWith("Duplicate digest detected");
    }

    @ParameterizedTest
    @ValueSource(strings = {"vc+sd-jwt", "dc+sd-jwt"})
    void validateDisclosures_whenDeeplyNested_thenSuccess(String credentialTyp) throws ParseException, JOSEException {

        List<Disclosure> disclosure = new ArrayList<>();

        var mgmtEntity = Management.builder()
                .id(UUID.randomUUID())
                .acceptedIssuerDids(List.of(DEFAULT_ISSUER_ID))
                .trustAnchors(List.of())
                .requestNonce(TEST_NONCE)
                .configurationOverride(new ConfigurationOverride(null, null, null, null, null, null))
                .build();

        var claimsForSdJWT = getClaimsFromSdJwt(disclosure);

        JWSHeader header =
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(new JOSEObjectType(credentialTyp)).build();

        JWTClaimsSet claimsSet = JWTClaimsSet.parse(claimsForSdJWT.build());
        SignedJWT jwt = new SignedJWT(header, claimsSet);
        ECKey privateKey = new ECKeyGenerator(Curve.P_256).generate();
        JWSSigner signer = new ECDSASigner(privateKey);
        jwt.sign(signer);

        SDJWT sdJwt = new SDJWT(jwt.serialize(), disclosure);
        SdJwt sdjwt = new SdJwt(sdJwt.toString());
        sdjwt.setClaims(claimsSet);

        assertDoesNotThrow(() -> verifier.validateDisclosures(sdjwt, mgmtEntity));
    }

    @Test
    void validateHeader_whenTypeHeaderMissing_thenInvalidFormatInsteadOfNPE() {
        // Arrange: JWS header without the optional "typ" claim (attacker-controllable, spec allows omitting it)
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(DEFAULT_KID_HEADER_VALUE).build();

        // Act + Assert: must throw a clean VerificationException instead of a NullPointerException
        var ex = assertThrows(VerificationException.class, () -> verifier.validateHeader(header));
        assertEquals(VerificationErrorResponseCode.INVALID_FORMAT, ex.getErrorResponseCode());
    }

    @Test
    void processDisclosures_whenSdClaimIsNotAnArray_thenMalformedCredentialInsteadOfClassCastException() {
        // Arrange: "_sd" present but not a JSON array (e.g. attacker sends a string instead)
        Map<String, Object> credentialSubject = new HashMap<>();
        credentialSubject.put("_sd", "not-an-array");

        JWTClaimsSet claimSet = new JWTClaimsSet.Builder()
                .claim("credentialSubject", credentialSubject)
                .build();

        // Act + Assert: must throw a clean VerificationException instead of a ClassCastException
        var ex = assertThrows(VerificationException.class,
                () -> verifier.processDisclosures(claimSet, List.of(), UUID.randomUUID()));
        assertEquals(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, ex.getErrorResponseCode());
        assertThat(ex.getErrorDescription()).contains("'_sd' claim must be a JSON array");
    }
}