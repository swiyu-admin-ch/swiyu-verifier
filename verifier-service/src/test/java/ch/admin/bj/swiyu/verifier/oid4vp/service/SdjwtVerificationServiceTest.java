package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.oid4vp.SdJwtVerificationService;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import com.authlete.sd.Disclosure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.*;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_KID_HEADER_VALUE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SdjwtVerificationServiceTest {

    private static final String TEST_NONCE = "test-nonce";
    private IssuerPublicKeyLoader issuerPublicKeyLoader;
    private StatusListReferenceFactory statusListReferenceFactory;
    private ObjectMapper objectMapper;
    private VerificationProperties verificationProperties;
    private Management managementEntity;
    private PresentationDefinition presentationDefinition;
    private SdJwtVerificationService verificationService;

    @BeforeEach
    void setUp() throws LoadingPublicKeyOfIssuerFailedException, JOSEException {
        issuerPublicKeyLoader = mock(IssuerPublicKeyLoader.class);
        statusListReferenceFactory = mock(StatusListReferenceFactory.class);
        objectMapper = new ObjectMapper();
        verificationProperties = mock(VerificationProperties.class);
        managementEntity = mock(Management.class);
        presentationDefinition = getMockedPresentationDefinition("ES256", "ES256", List.of("$.first_name", "$.last_name"));
        verificationService = new SdJwtVerificationService(issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);
        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);

        when(managementEntity.getId()).thenReturn(UUID.randomUUID());
        when(managementEntity.getAcceptedIssuerDids()).thenReturn(Collections.emptyList());
        when(managementEntity.getRequestNonce()).thenReturn(TEST_NONCE);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);
    }

    @Test
    void verifyPresentationWithTrustStatement_whenNotCanIssue_thenVerificationException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, NoSuchAlgorithmException, ParseException, JsonProcessingException {
        // Issuer not in Accepted Issuer dids
        var vcIssuerDid = "did:example:third";
        var vcIssuerKid = vcIssuerDid + "#key-1";
        when(issuerPublicKeyLoader.loadPublicKey(vcIssuerDid, vcIssuerKid))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        var emulator = new SDJWTCredentialMock(vcIssuerDid, vcIssuerKid);
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, "http://localhost");

        // Trust Statement for default vc type
        var trustRegistryUrl = "https://trust-registry.example.com";
        var trustIssuerDid = "did:example:other";
        var trustIssuerKid = trustIssuerDid + "#key-1";
        when(issuerPublicKeyLoader.loadPublicKey(trustIssuerDid, trustIssuerKid))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        var trustStatement = emulator.createTrustStatementIssuanceV1(trustIssuerDid, trustIssuerKid, DEFAULT_ISSUER_ID);
        when(managementEntity.getTrustAnchors())
                .thenReturn(List.of(new TrustAnchor(trustIssuerDid, trustRegistryUrl)));
        when(issuerPublicKeyLoader.loadTrustStatement(trustRegistryUrl, SDJWTCredentialMock.DEFAULT_VCT))
                .thenReturn((List.of(trustStatement)));
        var ex = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(vpToken, managementEntity));
        assertEquals(ISSUER_NOT_ACCEPTED, ex.getErrorResponseCode());
        assertEquals("Issuer not in list of accepted issuers or connected to trust anchor", ex.getErrorDescription());
    }

    @Test
    void verifyPresentationWithTrustStatement_thenSuccess() throws JOSEException, JsonProcessingException, LoadingPublicKeyOfIssuerFailedException, NoSuchAlgorithmException, ParseException {
        // Issuer not in Accepted Issuer dids
        var vcIssuerDid = "did:example:third";
        var vcIssuerKid = vcIssuerDid + "#key-1";
        when(issuerPublicKeyLoader.loadPublicKey(vcIssuerDid, vcIssuerKid))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        var emulator = new SDJWTCredentialMock(vcIssuerDid, vcIssuerKid);
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, "http://localhost");

        // Trust Statement for default vc type
        var trustRegistryUrl = "https://trust-registry.example.com";
        var trustIssuerDid = "did:example:other";
        var trustIssuerKid = trustIssuerDid + "#key-1";
        when(issuerPublicKeyLoader.loadPublicKey(trustIssuerDid, trustIssuerKid))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        var trustStatement = emulator.createTrustStatementIssuanceV1(trustIssuerDid, trustIssuerKid);
        when(managementEntity.getTrustAnchors())
                .thenReturn(List.of(new TrustAnchor(trustIssuerDid, trustRegistryUrl)));
        when(issuerPublicKeyLoader.loadTrustStatement(trustRegistryUrl, SDJWTCredentialMock.DEFAULT_VCT))
                .thenReturn((List.of(trustStatement)));
        assertDoesNotThrow(() -> verificationService.verifyVpTokenPresentationExchange(vpToken, managementEntity));
    }

    @Test
    void verifyPresentationWithInvalidJwt_noIssuer_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock("", DEFAULT_KID_HEADER_VALUE);
        var sdJWT = emulator.createSDJWTMock();

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of("did:example:other"));
        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(sdJWT, managementEntity));
        // No issuer is a trusted issuer ==> VC is refused
        // Note: There are other ways to provide an issuer than only the iss claim; namely the x5c header
        assertEquals(ISSUER_NOT_ACCEPTED, exception.getErrorResponseCode());
        assertEquals("Issuer not in list of accepted issuers or connected to trust anchor", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithInvalidJwt_issuerNotAccepted_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        String incorrectSdjwt = emulator.createSDJWTMock();
        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of("did:example:other"));
        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(incorrectSdjwt, managementEntity));
        assertEquals(ISSUER_NOT_ACCEPTED, exception.getErrorResponseCode());
        assertEquals("Issuer not in list of accepted issuers or connected to trust anchor", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithPrematureJwt_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var prematureSdJWT = emulator.createSDJWTMock(Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond());
        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(prematureSdJWT, managementEntity));
        assertEquals(JWT_PREMATURE, exception.getErrorResponseCode());
        assertEquals("Could not verify JWT credential is not yet valid", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithExpiredJwt_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var expiredSdJWT = emulator.createSDJWTMock(Instant.now().minus(24, ChronoUnit.HOURS).getEpochSecond(),
                Instant.now().minusSeconds(10).getEpochSecond());

        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(expiredSdJWT, managementEntity));

        assertEquals(JWT_EXPIRED, exception.getErrorResponseCode());
        assertEquals("Could not verify JWT credential is expired", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithIncorrectClaims_throwsException() throws NoSuchAlgorithmException, ParseException, JOSEException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var incompleteJwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(incompleteJwt, TEST_NONCE, "http://localhost");
        presentationDefinition = getMockedPresentationDefinition("ES384", "ES256", List.of("$.first_name", "$.last_name"));
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);
        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(vpToken, managementEntity));

        assertEquals(INVALID_FORMAT, exception.getErrorResponseCode());
        assertEquals("Invalid Algorithm: alg must be one of %s, but was %s".formatted(List.of("ES384"), "ES256"), exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithMissingKeyBinding_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);
        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(sdjwt, managementEntity));

        assertEquals(HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
        assertEquals("Missing Holder Key Binding Proof", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithWrongKeyBindingFormat_throwsException() throws JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var incorrectKeyBindingFormat = "not-kb+jwt";
        var vpToken = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, "http://localhost", Instant.now().getEpochSecond(), incorrectKeyBindingFormat);

        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(vpToken, managementEntity));

        assertEquals(HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
        assertEquals("Type of holder binding typ is expected to be kb+jwt but was %s".formatted(incorrectKeyBindingFormat), exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithInvalidKbTime_throwsException() throws JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, "http://localhost", Instant.now().getEpochSecond(), "kb+jwt");

        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(0);

        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);
        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(vpToken, managementEntity));

        assertEquals(HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
        assertTrue(exception.getErrorDescription().contains("Holder Binding proof was not issued at an acceptable time."));
    }

    @Test
    void verifyPresentationWithIncorrectNonce_throwsException() throws JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, "incorrect-test-nonce", "http://localhost");


        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(vpToken, managementEntity));

        assertEquals(MISSING_NONCE, exception.getErrorResponseCode());
        assertEquals("Holder Binding lacks correct nonce expected 'test-nonce' but was 'incorrect-test-nonce'", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithLoadingPublicKeyOfIssuerFailedException_throwsException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, "incorrect-test-nonce", "http://localhost");

        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenThrow(new LoadingPublicKeyOfIssuerFailedException("Failed to load public key for issuer", new Exception("Loading failed")));

        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(vpToken, managementEntity));

        assertEquals(PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE, exception.getErrorResponseCode());
    }


    @ParameterizedTest
    @ValueSource(strings = {"_sd", "..."})
    void verifyPresentationWithForbiddenSdJWTClaims_throwsException(String input) throws JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMockWithClaims(Map.of("test-key", "test-value", input, "this is forbidden"));
        var vpToken = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, "http://localhost", Instant.now().getEpochSecond(), "kb+jwt");

        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(vpToken, managementEntity));

        assertEquals(MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
        assertEquals("Illegal disclosure found with name _sd or ...", exception.getErrorDescription());
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "nbf", "exp", "cnf", "vct", "status"})
    void verifyPresentationWithForbiddenClaims_throwsException(String input) throws JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMockWithClaims(Map.of("test-key", "test-value", input, "this is forbidden"));
        var vpToken = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, "http://localhost", Instant.now().getEpochSecond(), "kb+jwt");
        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(vpToken, managementEntity));

        assertEquals(MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
        assertEquals("If present, the following registered JWT claims MUST be included in the SD-JWT and MUST NOT be included in the Disclosures: 'iss', 'nbf', 'exp', 'cnf', 'vct', 'status'", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationDuplicates_throwsException() throws JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        // duplicate disclosures
        var parts = sdjwt.split("~");
        sdjwt = parts[0] + "~" + parts[1] + "~" + parts[1] + "~";
        var vpToken = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, "http://localhost");
        var exception = assertThrows(VerificationException.class, () -> verificationService.verifyVpTokenPresentationExchange(vpToken, managementEntity));

        assertEquals(MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
        assertEquals("Request contains non-distinct disclosures", exception.getErrorDescription());
    }

    @Test
    void testCheckPresentationDefinitionCriteriaWithNull() throws NoSuchAlgorithmException, ParseException, JOSEException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, "http://localhost");
        var vpTokenParts = vpToken.split("~");

        var jwt = SignedJWT.parse(vpTokenParts[0]);
        assertTrue(jwt.verify(new ECDSAVerifier(KeyFixtures.issuerKey())), "Should be able to verify JWT");


        var payload = jwt.getJWTClaimsSet();

        List<Disclosure> disclosures = Arrays.stream(Arrays.copyOfRange(vpTokenParts, 1, vpTokenParts.length - 1))
                .map(Disclosure::parse).toList();

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );

        presentationDefinition = getMockedPresentationDefinition("ES384", "ES256", List.of("$.first_name", "$.last_name", "$.not_existing"));

        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);
        var claims = payload.getClaims();
        assertThrows(VerificationException.class, () ->
                verifier.checkPresentationDefinitionCriteria(claims, disclosures)
        );
    }

    // createField("$.first_name"), createField("$.last_name"), createField("$.not_existing")
    private PresentationDefinition getMockedPresentationDefinition(String alg, String keyBindingAlg, List<String> requiredFieldPaths) {
        Map<String, PresentationDefinition.FormatAlgorithm> formatAlgorithms = Map.of(
                "vc+sd-jwt", new PresentationDefinition.FormatAlgorithm(List.of(alg), List.of(keyBindingAlg))
        );

        var fields = requiredFieldPaths.stream()
                .map(this::createField)
                .toList();

        PresentationDefinition.Constraint constraint = new PresentationDefinition.Constraint(
                UUID.randomUUID().toString(), "test-constraint", "Test Constraint", formatAlgorithms, fields);

        PresentationDefinition.InputDescriptor inputDescriptor = new PresentationDefinition.InputDescriptor(UUID.randomUUID().toString(), "test-input-descriptor", "Test Input Descriptor", formatAlgorithms, constraint);

        return new PresentationDefinition(
                "test-pd", "Test Presentation Definition", "Test Purpose", formatAlgorithms, List.of(inputDescriptor)
        );
    }

    private PresentationDefinition.Field createField(String fieldPath) {
        return new PresentationDefinition.Field(
                List.of(fieldPath), "test-field", "Test Field", "purpose", null);
    }
}