package ch.admin.bj.swiyu.verifier.service.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import com.authlete.sd.Disclosure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.*;

import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_KID_HEADER_VALUE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SdjwtCredentialVerifierTest {

    private static final String TEST_NONCE = "test-nonce";
    private IssuerPublicKeyLoader issuerPublicKeyLoader;
    private StatusListReferenceFactory statusListReferenceFactory;
    private ObjectMapper objectMapper;
    private VerificationProperties verificationProperties;
    private ApplicationProperties applicationProperties;
    private Management managementEntity;
    private PresentationDefinition presentationDefinition;

    @BeforeEach
    void setUp() throws LoadingPublicKeyOfIssuerFailedException, JOSEException {
        issuerPublicKeyLoader = mock(IssuerPublicKeyLoader.class);
        statusListReferenceFactory = mock(StatusListReferenceFactory.class);
        objectMapper = new ObjectMapper();
        verificationProperties = mock(VerificationProperties.class);
        applicationProperties = mock(ApplicationProperties.class);
        managementEntity = mock(Management.class);
        presentationDefinition = getMockedPresentationDefinition("ES256", "ES256", List.of("$.first_name", "$.last_name"));
        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);
        when(applicationProperties.getClientId()).thenReturn("did:example:12345");
        when(applicationProperties.getExternalUrl()).thenReturn("did:example:12345");
        when(managementEntity.getId()).thenReturn(UUID.randomUUID());
        when(managementEntity.getRequestNonce()).thenReturn(TEST_NONCE);

        when(managementEntity.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(issuerPublicKeyLoader.loadPublicKey(DEFAULT_ISSUER_ID, DEFAULT_KID_HEADER_VALUE))
                .thenReturn(KeyFixtures.issuerKey().toPublicKey());
        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);
    }


    @Test
    void testCheckPresentationDefinitionCriteriaWithNull() throws NoSuchAlgorithmException, ParseException, JOSEException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdjwt = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdjwt, TEST_NONCE, "did:example:12345");
        var vpTokenParts = vpToken.split("~");

        var jwt = SignedJWT.parse(vpTokenParts[0]);
        assertTrue(jwt.verify(new ECDSAVerifier(KeyFixtures.issuerKey())), "Should be able to verify JWT");


        var payload = jwt.getJWTClaimsSet();

        List<Disclosure> disclosures = Arrays.stream(Arrays.copyOfRange(vpTokenParts, 1, vpTokenParts.length - 1))
                .map(Disclosure::parse).toList();

        SdjwtCredentialVerifier verifier = new SdjwtCredentialVerifier(
                vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties, applicationProperties
        );

        presentationDefinition = getMockedPresentationDefinition("ES384", "ES256", List.of("$.first_name", "$.last_name", "$.not_existing"));

        when(managementEntity.getRequestedPresentation()).thenReturn(presentationDefinition);
        var claims = payload.getClaims();
        assertThrows(VerificationException.class, () ->
                verifier.checkPresentationDefinitionCriteria(claims, disclosures)
        );
    }
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