package ch.admin.bj.swiyu.verifier.service.oid4vp;
import ch.admin.bj.swiyu.sdjwtvalidator.SdJwtVcValidator;
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
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.eid.did_sidekicks.DidDoc;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
/**
 * Unit tests for {@link SdJwtVpTokenVerifier} focusing on trust evaluation and holder binding audience checks.
 */
class SdJwtVpTokenVerifierTest {
    private static final String TEST_NONCE = "test-nonce";
    private SdJwtVcValidator sdJwtVcValidator;
    private DidResolverFacade didResolverFacade;
    private IssuerPublicKeyLoader issuerPublicKeyLoader;
    private StatusListReferenceFactory statusListReferenceFactory;
    private ApplicationProperties applicationProperties;
    private VerificationProperties verificationProperties;
    private Management management;
    private SdJwtVpTokenVerifier verifier;
    @BeforeEach
    void setUp() throws Exception {
        sdJwtVcValidator = mock(SdJwtVcValidator.class);
        didResolverFacade = mock(DidResolverFacade.class);
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
        // Default: signature validation succeeds
        DidDoc mockDidDoc = mock(DidDoc.class);
        when(sdJwtVcValidator.getAndValidateResolutionUrl(anyString())).thenReturn(DEFAULT_ISSUER_ID);
        when(didResolverFacade.resolveDid(DEFAULT_ISSUER_ID)).thenReturn(mockDidDoc);
        doNothing().when(sdJwtVcValidator).validateSdJwtVc(anyString(), any(DidDoc.class));
        // Status list verification is out of scope of this unit, so we simulate "no status entries"
        when(statusListReferenceFactory.createStatusListReferences(any(), any())).thenReturn(List.of());
        verifier = new SdJwtVpTokenVerifier(sdJwtVcValidator, didResolverFacade, statusListReferenceFactory, applicationProperties, verificationProperties);
    }
    @Test
    void verifyVpToken_Legacy_whenTrustAnchorCanIssue_thenSucceeds() throws Exception {
        // Arrange: VC issued by third party, not directly trusted via acceptedIssuerDids
        var vcIssuerDid = "did:example:third";
        var vcIssuerKid = vcIssuerDid + "#key-1";
        DidDoc vcIssuerDidDoc = mock(DidDoc.class);
        when(sdJwtVcValidator.getAndValidateResolutionUrl(anyString())).thenReturn(vcIssuerDid);
        when(didResolverFacade.resolveDid(vcIssuerDid)).thenReturn(vcIssuerDidDoc);
        doNothing().when(sdJwtVcValidator).validateSdJwtVc(anyString(), any(DidDoc.class));
        var emulator = new SDJWTCredentialMock(vcIssuerDid, vcIssuerKid);
        var sdjwt = emulator.createSDJWTMock();
        var vpTokenString = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, applicationProperties.getClientId());
        var sdJwt = new SdJwt(vpTokenString);
        // Trust Statement: separate trust anchor vouches that vcIssuerDid canIssue DEFAULT_VCT
        var trustRegistryUrl = "https://trust-registry.example.com";
        var trustIssuerDid = "did:example:trust";
        var trustIssuerKid = trustIssuerDid + "#key-1";
        var trustStatement = emulator.createTrustStatementIssuanceV1(trustIssuerDid, trustIssuerKid, vcIssuerDid);
        when(management.getTrustAnchors())
                .thenReturn(List.of(new TrustAnchor(trustIssuerDid, trustRegistryUrl)));
        when(issuerPublicKeyLoader.loadTrustStatement(trustRegistryUrl, SDJWTCredentialMock.DEFAULT_VCT))
                .thenReturn(List.of(trustStatement));
        // Act
        SdJwt verified = verifier.verifyVpTokenTrustStatement(sdJwt, management);
        // Assert
        assertThat(verified.getClaims()).isNotNull();
        assertThat(verified.getHeader()).isNotNull();
    }
    @Test
    void validateKeyBinding_whenAudienceMismatch_thenHolderBindingMismatch() throws JOSEException, NoSuchAlgorithmException, ParseException {
        // Arrange: valid SD-JWT with key binding, but audience is not our clientId
        var emulator = new SDJWTCredentialMock(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE);
        var sdjwt = emulator.createSDJWTMock();
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
        Map<String, Object> credentialSubject = new HashMap<>();
        credentialSubject.put("_sd", List.of(digest));
        credentialSubject.put(claimName, "Alice");
        JWTClaimsSet claimSet = new JWTClaimsSet.Builder()
                .claim("credentialSubject", credentialSubject)
                .build();
        var ex = assertThrows(VerificationException.class,
                () -> verifier.processDisclosures(claimSet, List.of(disclosure), UUID.randomUUID()));
        assertThat(ex.getErrorResponseCode())
                .isEqualTo(VerificationErrorResponseCode.MALFORMED_CREDENTIAL);
        assertThat(ex.getErrorDescription())
                .isEqualTo("Claim name already exists at the level of the _sd key");
    }
    @ParameterizedTest
    @ValueSource(strings = {"_sd", "..."})
    void processDisclosures_whenInvalidDisclosure_thenMalformedCredential(String invalidInput) {
        var salt = "salt-1";
        var claimValue = List.of();
        Disclosure disclosure = new Disclosure(salt, invalidInput, claimValue);
        String digest = disclosure.digest();
        Map<String, Object> credentialSubject = new HashMap<>();
        credentialSubject.put("_sd", List.of(digest));
        JWTClaimsSet claimSet = new JWTClaimsSet.Builder()
                .claim("credentialSubject", credentialSubject)
                .build();
        var ex = assertThrows(VerificationException.class,
                () -> verifier.processDisclosures(claimSet, List.of(disclosure), UUID.randomUUID()));
        assertThat(ex.getErrorResponseCode()).isEqualTo(VerificationErrorResponseCode.MALFORMED_CREDENTIAL);
        assertThat(ex.getErrorDescription()).isEqualTo("Illegal disclosure found with name _sd or ...");
    }
    @Test
    void processDisclosures_whenDeeplyNested_thenSuccess() throws ParseException {
        List<Disclosure> disclosure = new ArrayList<>();
        var claimsForSdJWT = getClaimsFromSdJwt(disclosure);
        JWTClaimsSet claimsSet = JWTClaimsSet.parse(claimsForSdJWT.build());
        assertDoesNotThrow(() -> verifier.processDisclosures(claimsSet, disclosure, UUID.randomUUID()));
    }
    @Test
    void processDisclosuresRecursive_withDuplicatedDigest_thenError() throws ParseException {
        List<Disclosure> disclosure = new ArrayList<>();
        var claimsForSdJWT = getClaimsFromWithDuplicatedDigestsSdJwt(disclosure);
        JWTClaimsSet claimsSet = JWTClaimsSet.parse(claimsForSdJWT.build());
        var ex = assertThrows(VerificationException.class,
                () -> verifier.processDisclosures(claimsSet, disclosure, UUID.randomUUID()));
        assertThat(ex.getErrorResponseCode()).isEqualTo(VerificationErrorResponseCode.MALFORMED_CREDENTIAL);
        assertThat(ex.getErrorDescription()).startsWith("Duplicate digest detected");
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
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("vc+sd-jwt")).build();
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
    // ---- helper methods copied from original test ----
    private static SDObjectBuilder getClaimsFromSdJwt(List<Disclosure> disclosure) {
        var addressBuilder = new SDObjectBuilder();
        addSDClaim(addressBuilder, disclosure, "street_address", "123 Main St");
        addSDClaim(addressBuilder, disclosure, "locality", "Anytown");
        addSDClaim(addressBuilder, disclosure, "region", "Anystate");
        addSDClaim(addressBuilder, disclosure, "country", "US");

        var claimsForSdJWT = new SDObjectBuilder();
        addSDClaim(claimsForSdJWT, disclosure, "sub", "user_42");
        addSDClaim(claimsForSdJWT, disclosure, "given_name", "John");
        addSDClaim(claimsForSdJWT, disclosure, "family_name", "Doe");
        addSDClaim(claimsForSdJWT, disclosure, "email", "johndoe@example.com");
        addSDClaim(claimsForSdJWT, disclosure, "phone_number", "+1-202-555-0101");
        var addressDisc = new Disclosure("address", addressBuilder.build());
        disclosure.add(addressDisc);
        claimsForSdJWT.putSDClaim(addressDisc);
        addSDClaim(claimsForSdJWT, disclosure, "birthdate", "1940-01-01");
        return claimsForSdJWT;
    }

    private static void addSDClaim(SDObjectBuilder builder, List<Disclosure> disclosures, String name, Object value) {
        var disc = new Disclosure(name, value);
        builder.putSDClaim(disc);
        disclosures.add(disc);
    }

    private static SDObjectBuilder getClaimsFromWithDuplicatedDigestsSdJwt(List<Disclosure> disclosure) {
        var claimsForSdJWT = new SDObjectBuilder();
        var disc = new Disclosure("sub", "user_42");
        claimsForSdJWT.putSDClaim(disc);
        disclosure.add(disc);
        // Add the same disclosure again to simulate duplicates
        disclosure.add(disc);
        return claimsForSdJWT;
    }
}
