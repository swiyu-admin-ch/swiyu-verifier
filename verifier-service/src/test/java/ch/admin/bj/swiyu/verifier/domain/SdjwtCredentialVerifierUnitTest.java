package ch.admin.bj.swiyu.verifier.domain;

import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.*;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_KID_HEADER_VALUE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SdjwtCredentialVerifierUnitTest {

    private IssuerPublicKeyLoader issuerPublicKeyLoader;
    private StatusListReferenceFactory statusListReferenceFactory;
    private ObjectMapper objectMapper;
    private VerificationProperties verificationProperties;
    private Management managementEntity;

    @BeforeEach
    void setUp() {
        issuerPublicKeyLoader = mock(IssuerPublicKeyLoader.class);
        statusListReferenceFactory = mock(StatusListReferenceFactory.class);
        objectMapper = new ObjectMapper();
        verificationProperties = mock(VerificationProperties.class);
        managementEntity = mock(Management.class);

        when(managementEntity.getId()).thenReturn(UUID.randomUUID());
        when(managementEntity.getAcceptedIssuerDids()).thenReturn(Collections.emptyList());
        when(managementEntity.getRequestNonce()).thenReturn("test-nonce");
    }

    @Test
    void verifyPresentationWithInvalidJwt_noIssuer_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock("", DEFAULT_KID_HEADER_VALUE);
        var sdJWT = emulator.createSDJWTMock();

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of("did:example:other"));
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                sdJWT, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);
        assertEquals(MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
        assertEquals("Missing issuer in the JWT token", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithInvalidJwt_issuerNotAccepted_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        String incorrectSdjwt = emulator.createSDJWTMock();
        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of("did:example:other"));
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                incorrectSdjwt, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);
        assertEquals(ISSUER_NOT_ACCEPTED, exception.getErrorResponseCode());
        assertEquals("Issuer not in list of accepted issuers", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithPrematureJwt_throwsException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var prematureSdJWT = emulator.createSDJWTMock(Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond());

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                prematureSdJWT, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
        assertEquals("Could not verify JWT credential is not yet valid", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithExpiredJwt_throwsException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var expiredSdJWT = emulator.createSDJWTMock(Instant.now().minus(24, ChronoUnit.HOURS).getEpochSecond(),
                Instant.now().minusSeconds(10).getEpochSecond());

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                expiredSdJWT, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(JWT_EXPIRED, exception.getErrorResponseCode());
        assertEquals("Could not verify JWT credential is expired", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithIncorrectClaims_throwsException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var prematureSdJWT = emulator.createSDJWTMock();

        PresentationDefinition presentationDefinition = getMockedPresentationDefinition("ES384", "ES256");

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                prematureSdJWT, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(INVALID_FORMAT, exception.getErrorResponseCode());
        assertEquals("Invalid algorithm: %s requested %s".formatted("ES256", List.of("ES384")), exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithMissingKeyBinding_throwsException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var prematureSdJWT = emulator.createSDJWTMock();

        PresentationDefinition presentationDefinition = getMockedPresentationDefinition("ES256", "ES256");

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                prematureSdJWT, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
        assertEquals("Missing Holder Key Binding Proof", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithWrongKeyBindingFormat_throwsException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var incorrectKeyBindingFormat = "not-kb+jwt";
        var vpToken = emulator.addKeyBindingProof(sdjwt, "test-nonce", "http://localhost", Instant.now().getEpochSecond(), incorrectKeyBindingFormat);

        PresentationDefinition presentationDefinition = getMockedPresentationDefinition("ES256", "ES256");

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
        assertEquals("Type of holder binding typ is expected to be kb+jwt but was %s".formatted(incorrectKeyBindingFormat), exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithKeyBinding_throwsException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var expectedKeyBindingAlg = "ES384";
        var vpToken = emulator.addKeyBindingProof(sdjwt, "test-nonce", "http://localhost", Instant.now().getEpochSecond(), "kb+jwt");

        PresentationDefinition presentationDefinition = getMockedPresentationDefinition("ES256", "ES384");

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
        assertEquals("Holder binding algorithm must be in %s".formatted(List.of(expectedKeyBindingAlg)), exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithInvalidKbTime_throwsException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, "test-nonce", "http://localhost", Instant.now().getEpochSecond(), "kb+jwt");

        PresentationDefinition presentationDefinition = getMockedPresentationDefinition("ES256", "ES256");

        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(0);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
        assertTrue(exception.getErrorDescription().contains("Holder Binding proof was not issued at an acceptable time."));
    }

    @Test
    void verifyPresentationWithIncorrectNonce_throwsException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, "incorrect-test-nonce", "http://localhost");

        PresentationDefinition presentationDefinition = getMockedPresentationDefinition("ES256", "ES256");

        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(MISSING_NONCE, exception.getErrorResponseCode());
        assertEquals("Holder Binding lacks correct nonce expected 'test-nonce' but was 'incorrect-test-nonce'", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithLoadingPublicKeyOfIssuerFailedException_throwsException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, "incorrect-test-nonce", "http://localhost");

        PresentationDefinition presentationDefinition = getMockedPresentationDefinition("ES256", "ES256");

        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenThrow(new LoadingPublicKeyOfIssuerFailedException("Failed to load public key for issuer", new Exception("Loading failed")));

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE, exception.getErrorResponseCode());
    }

    // TODO: validateSDHash

    @ParameterizedTest
    @ValueSource(strings = {"_sd", "..."})
    void verifyPresentationWithForbiddenSdJWTClaims_throwsException(String input) throws LoadingPublicKeyOfIssuerFailedException, JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMockWithClaims(Map.of("test-key", "test-value", input, "this is forbidden"));
        var vpToken = emulator.addKeyBindingProof(sdjwt, "test-nonce", "http://localhost", Instant.now().getEpochSecond(), "kb+jwt");

        PresentationDefinition presentationDefinition = getMockedPresentationDefinition("ES256", "ES256");

        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
        assertEquals("Illegal disclosure found with name _sd or ...", exception.getErrorDescription());
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "nbf", "exp", "cnf", "vct", "status"})
    void verifyPresentationWithForbiddenClaims_throwsException(String input) throws LoadingPublicKeyOfIssuerFailedException, JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMockWithClaims(Map.of("test-key", "test-value", input, "this is forbidden"));
        var vpToken = emulator.addKeyBindingProof(sdjwt, "test-nonce", "http://localhost", Instant.now().getEpochSecond(), "kb+jwt");

        PresentationDefinition presentationDefinition = getMockedPresentationDefinition("ES256", "ES256");

        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
        assertEquals("If present, the following registered JWT claims MUST be included in the SD-JWT and MUST NOT be included in the Disclosures: 'iss', 'nbf', 'exp', 'cnf', 'vct', 'status'", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationDuplicates_throwsException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();

        // duplicate disclosures
        var parts = sdjwt.split("~");
        sdjwt = parts[0] + "~" + parts[1] + "~" + parts[1] + "~";

        var vpToken = emulator.addKeyBindingProof(sdjwt, "test-nonce", "http://localhost");

        PresentationDefinition presentationDefinition = getMockedPresentationDefinition("ES256", "ES256");

        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
        assertEquals("Request contains non-distinct disclosures", exception.getErrorDescription());
    }

    private PresentationDefinition getMockedPresentationDefinition(String alg, String keyBindingAlg) {
        Map<String, PresentationDefinition.FormatAlgorithm> formatAlgorithms = Map.of(
                "vc+sd-jwt", new PresentationDefinition.FormatAlgorithm(List.of(alg), List.of(keyBindingAlg))
        );

        PresentationDefinition.Constraint constraint = new PresentationDefinition.Constraint(
                UUID.randomUUID().toString(), "test-constraint", "Test Constraint", formatAlgorithms, List.of()
        );

        PresentationDefinition.InputDescriptor inputDescriptor = new PresentationDefinition.InputDescriptor(UUID.randomUUID().toString(), "test-input-descriptor", "Test Input Descriptor", formatAlgorithms, constraint);

        return new PresentationDefinition(
                "test-pd", "Test Presentation Definition", "Test Purpose", formatAlgorithms, List.of(inputDescriptor)
        );
    }
}