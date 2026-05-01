package ch.admin.bj.swiyu.verifier.domain;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.sdjwtvalidator.SdJwtVcValidator;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.eid.did_sidekicks.DidDoc;
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
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_KID_HEADER_VALUE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Legacy for DIF Presentation Exchange
 */
class SdjwtCredentialVerifierTest {

    private final String testNonce = "test-nonce";
    private SdJwtVcValidator sdJwtVcValidator;
    private DidResolverFacade didResolverFacade;
    private IssuerPublicKeyLoader issuerPublicKeyLoader;
    private StatusListReferenceFactory statusListReferenceFactory;
    private ObjectMapper objectMapper;
    private VerificationProperties verificationProperties;
    private ApplicationProperties applicationProperties;
    private Management managementEntity;
    private PresentationDefinition presentationDefinition;

    @BeforeEach
    void setUp() throws Exception {
        sdJwtVcValidator = mock(SdJwtVcValidator.class);
        didResolverFacade = mock(DidResolverFacade.class);
        issuerPublicKeyLoader = mock(IssuerPublicKeyLoader.class);
        statusListReferenceFactory = mock(StatusListReferenceFactory.class);
        objectMapper = new ObjectMapper();
        verificationProperties = mock(VerificationProperties.class);
        applicationProperties = mock(ApplicationProperties.class);
        managementEntity = mock(Management.class);
        presentationDefinition = getMockedPresentationDefinition("ES256", "ES256", List.of("$.first_name", "$.last_name"));

        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);
        when(applicationProperties.getClientId()).thenReturn("did:example:12345");
        when(applicationProperties.getExternalUrl()).thenReturn("http://localhost");
        DidDoc mockDidDoc = mock(DidDoc.class);
        when(sdJwtVcValidator.getAndValidateResolutionUrl(any())).thenReturn(DEFAULT_ISSUER_ID);
        when(didResolverFacade.resolveDid(any())).thenReturn(mockDidDoc);
        doNothing().when(sdJwtVcValidator).validateSdJwtVc(any(), any(DidDoc.class));
        when(managementEntity.getId()).thenReturn(UUID.randomUUID());
        when(managementEntity.getAcceptedIssuerDids()).thenReturn(Collections.emptyList());
        when(managementEntity.getRequestNonce()).thenReturn(testNonce);
        when(managementEntity.getConfigurationOverride()).thenReturn(new ConfigurationOverride(null, null, null, null, null));

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);
    }

    @Test
    void verifyPresentationWithTrustStatement_whenNotCanIssue_thenVerificationException() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, NoSuchAlgorithmException, ParseException, JsonProcessingException {
        // Issuer not in Accepted Issuer dids
        var vcIssuerDid = "did:example:third";
        var vcIssuerKid = vcIssuerDid + "#key-1";
        var emulator = new SDJWTCredentialMock(vcIssuerDid, vcIssuerKid);
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, testNonce, applicationProperties.getExternalUrl());

        // Trust Statement for default vc type
        var trustRegistryUrl = "https://trust-registry.example.com";
        var trustIssuerDid = "did:example:other";
        var trustIssuerKid = trustIssuerDid + "#key-1";
        var trustStatement = emulator.createTrustStatementIssuanceV1(trustIssuerDid, trustIssuerKid, DEFAULT_ISSUER_ID);
        when(managementEntity.getTrustAnchors())
                .thenReturn(List.of(new TrustAnchor(trustIssuerDid, trustRegistryUrl)));
        when(managementEntity.getConfigurationOverride()).thenReturn(new ConfigurationOverride(null, null, null, null, null));
        when(issuerPublicKeyLoader.loadTrustStatement(trustRegistryUrl, SDJWTCredentialMock.DEFAULT_VCT))
                .thenReturn((List.of(trustStatement)));
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var ex = assertThrows(VerificationException.class, verifier::verifyPresentation);
        assertEquals(ISSUER_NOT_ACCEPTED, ex.getErrorResponseCode());
        assertEquals("Issuer not in list of accepted issuers or connected to trust anchor", ex.getErrorDescription());
    }

    @ParameterizedTest
    @ValueSource(strings = {"did:example:12345"})
    void verifyPresentationWithTrustStatement_thenSuccess(String audience) throws JOSEException, JsonProcessingException, LoadingPublicKeyOfIssuerFailedException, NoSuchAlgorithmException, ParseException {
        // Issuer not in Accepted Issuer dids
        var vcIssuerDid = "did:example:third";
        var vcIssuerKid = vcIssuerDid + "#key-1";
        var emulator = new SDJWTCredentialMock(vcIssuerDid, vcIssuerKid);
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, testNonce, audience);

        // Trust Statement for default vc type
        var trustRegistryUrl = "https://trust-registry.example.com";
        var trustIssuerDid = "did:example:other";
        var trustIssuerKid = trustIssuerDid + "#key-1";
        var trustStatement = emulator.createTrustStatementIssuanceV1(trustIssuerDid, trustIssuerKid);
        when(managementEntity.getTrustAnchors())
                .thenReturn(List.of(new TrustAnchor(trustIssuerDid, trustRegistryUrl)));
        when(issuerPublicKeyLoader.loadTrustStatement(trustRegistryUrl, SDJWTCredentialMock.DEFAULT_VCT))
                .thenReturn((List.of(trustStatement)));
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        assertDoesNotThrow(verifier::verifyPresentation);
    }

    @Test
    void verifyPresentationWithInvalidJwt_noIssuer_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock("", DEFAULT_KID_HEADER_VALUE);
        var sdJWT = emulator.createSDJWTMock();

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of("did:example:other"));
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                sdJWT, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);
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
        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                incorrectSdjwt, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);
        assertEquals(ISSUER_NOT_ACCEPTED, exception.getErrorResponseCode());
        assertEquals("Issuer not in list of accepted issuers or connected to trust anchor", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithPrematureJwt_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var prematureSdJWT = emulator.createSDJWTMock(Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond());

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                prematureSdJWT, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(JWT_PREMATURE, exception.getErrorResponseCode());
        assertEquals("Could not verify JWT credential is not yet valid", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithExpiredJwt_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var expiredSdJWT = emulator.createSDJWTMock(Instant.now().minus(24, ChronoUnit.HOURS).getEpochSecond(),
                Instant.now().minusSeconds(10).getEpochSecond());

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                expiredSdJWT, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(JWT_EXPIRED, exception.getErrorResponseCode());
        assertEquals("Could not verify JWT credential is expired", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithIncorrectClaims_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var prematureSdJWT = emulator.createSDJWTMock();

        presentationDefinition = getMockedPresentationDefinition("ES384", "ES256", List.of("$.first_name", "$.last_name"));

        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                prematureSdJWT, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(INVALID_FORMAT, exception.getErrorResponseCode());
        assertEquals("Invalid Algorithm: alg must be one of %s, but was %s".formatted(List.of("ES384"), "ES256"), exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithMissingKeyBinding_throwsException() {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var prematureSdJWT = emulator.createSDJWTMock();


        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                prematureSdJWT, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
        assertEquals("Missing Holder Key Binding Proof", exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithWrongKeyBindingFormat_throwsException() throws JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var incorrectKeyBindingFormat = "not-kb+jwt";
        var vpToken = emulator.addKeyBindingProof(sdjwt, testNonce, applicationProperties.getExternalUrl(), Instant.now().getEpochSecond(), incorrectKeyBindingFormat);


        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
        assertEquals("Type of holder binding typ is expected to be kb+jwt but was %s".formatted(incorrectKeyBindingFormat), exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithKeyBinding_throwsException() throws JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var expectedKeyBindingAlg = "ES384";
        var vpToken = emulator.addKeyBindingProof(sdjwt, testNonce, applicationProperties.getExternalUrl(), Instant.now().getEpochSecond(), "kb+jwt");

        presentationDefinition = getMockedPresentationDefinition("ES256", "ES384", List.of("$.first_name", "$.last_name"));

        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
        assertEquals("Holder binding algorithm must be in %s".formatted(List.of(expectedKeyBindingAlg)), exception.getErrorDescription());
    }

    @Test
    void verifyPresentationWithInvalidKbTime_throwsException() throws JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, testNonce, applicationProperties.getExternalUrl(), Instant.now().getEpochSecond(), "kb+jwt");


        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(0);

        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
        assertTrue(exception.getErrorDescription().contains("Holder Binding proof was not issued at an acceptable time."));
    }

    @Test
    void verifyPresentationWithIncorrectNonce_throwsException() throws JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, "incorrect-test-nonce", applicationProperties.getExternalUrl());


        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);

        assertEquals(MISSING_NONCE, exception.getErrorResponseCode());
        assertEquals("Holder Binding lacks correct nonce expected 'test-nonce' but was 'incorrect-test-nonce'", exception.getErrorDescription());
    }

    @Test
    void verifyPresentation_whenSignatureValidationFails_throwsException() throws JOSEException, NoSuchAlgorithmException, ParseException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, testNonce, applicationProperties.getExternalUrl());

        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);
        doThrow(new ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException("Signature invalid"))
                .when(sdJwtVcValidator).validateSdJwtVc(any(), any(DidDoc.class));

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, sdJwtVcValidator, didResolverFacade, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );
        var exception = assertThrows(VerificationException.class, verifier::verifyPresentation);
        assertEquals(PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE, exception.getErrorResponseCode());
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

