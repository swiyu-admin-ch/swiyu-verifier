/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain;

import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationError;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverAdapter;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverException;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.eid.didtoolbox.TrustDidWebException;
import com.authlete.sd.Disclosure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition.*;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.DidDocFixtures.issuerDidDoc;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.ManagementFixtures.managementEntity;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.PresentationDefinitionFixtures.presentationDefinitionWithFields;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.PresentationDefinitionFixtures.sdwjtPresentationDefinition;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_KID_HEADER_VALUE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class SdjwtCredentialVerifierTest {
    @MockitoBean
    private DidResolverAdapter didResolverAdapter;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IssuerPublicKeyLoader issuerPublicKeyLoader;
    @MockitoBean
    private StatusListReferenceFactory statusListReferenceFactory;
    @Autowired
    private VerificationProperties verificationProperties;

    private UUID id;
    private String sdJWTCredential;
    private List<Disclosure> disclosures;
    private Jws<Claims> claims;
    private Management managementEntity;
    private PresentationDefinition presentationDefinition;
    private SDJWTCredentialMock emulator;

    @BeforeEach
    void setUp() throws Exception {

        // create a SD JWT Token
        emulator = new SDJWTCredentialMock();
        id = UUID.randomUUID();
        sdJWTCredential = emulator.createSDJWTMock();
        presentationDefinition = sdwjtPresentationDefinition(id, List.of("$.first_name", "$.last_name", "$.birthdate"));
        managementEntity = managementEntity(id, presentationDefinition);

        var parts = sdJWTCredential.split("~");
        disclosures = Arrays.stream(Arrays.copyOfRange(parts, 1, parts.length)).map(Disclosure::parse).toList();

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);
        var publicKey = issuerPublicKeyLoader.loadPublicKey(emulator.getIssuerId(), emulator.getKidHeaderValue());

        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(parts[0]);
        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    @Test
    void testCompletenessOfSDJWTWithPresentationDefinition_thenSuccess() {
        var cred = new SdjwtCredentialVerifier(sdJWTCredential, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);

        assertFalse(cred.checkPresentationDefinitionCriteria(claims.getPayload(), disclosures).isEmpty());
    }

    @Test
    void testCompletenessOfSDJWTWithPresentationDefinitionWithAdditionalDef_thenError() {

        presentationDefinition = sdwjtPresentationDefinition(id, List.of("$.first_name", "$.last_name", "$.birthdate", "$.definitely_not_there"));
        managementEntity = managementEntity(id, presentationDefinition);
        var cred = new SdjwtCredentialVerifier(sdJWTCredential, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);
        var payload = claims.getPayload();

        assertThrows(VerificationException.class, () -> cred.checkPresentationDefinitionCriteria(payload, disclosures));
    }

    @Test
    void testCompletenessOfSDJWTWithPresentationDefinitionWithFilter_thenSuccess() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {
        // Form Credential Request
        HashMap<String, FormatAlgorithm> formats = new HashMap<>();
        formats.put("vc+sd-jwt", FormatAlgorithm.builder()
                .keyBindingAlg(List.of("ES256"))
                .alg(List.of("ES256"))
                .build());

        presentationDefinition = presentationDefinitionWithFields(
                id,
                List.of(
                        Field.builder().path(List.of("$.vct")).filter(Filter.builder().type("string").constDescriptor(SDJWTCredentialMock.DEFAULT_VCT).build()).build(),
                        Field.builder().path(List.of("$.last_name")).build(),
                        Field.builder().path(List.of("$.birthdate")).build()
                ),
                null,
                formats
        );

        managementEntity = managementEntity(id, presentationDefinition);

        sdJWTCredential = emulator.createSDJWTMock();

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);
        cred.verifyPresentation();
    }

    @Test
    void testCompletenessOfSDJWTWithPresentationDefinitionWithFilterWrongVC_thenFailure() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {
        // Form Credential Request
        HashMap<String, FormatAlgorithm> formats = new HashMap<>();
        formats.put("vc+sd-jwt", FormatAlgorithm.builder()
                .keyBindingAlg(List.of("ES256"))
                .alg(List.of("ES256"))
                .build());

        presentationDefinition = presentationDefinitionWithFields(
                id,
                List.of(
                        Field.builder().path(List.of("$.vct")).filter(Filter.builder().type("string").constDescriptor("SomeOtherVCTWeDontHave").build()).build(),
                        Field.builder().path(List.of("$.last_name")).build(),
                        Field.builder().path(List.of("$.birthdate")).build()
                ),
                null,
                formats
        );

        managementEntity = managementEntity(id, presentationDefinition);

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);
        assertThrows(VerificationException.class, cred::verifyPresentation);
        // We will not have the failed state here, as this is set in th exception handling
    }

    @Test
    void testMultipleVCT_thenSuccess() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {
        // Form Credential Request
        HashMap<String, FormatAlgorithm> formats = new HashMap<>();
        formats.put("vc+sd-jwt", FormatAlgorithm.builder()
                .keyBindingAlg(List.of("ES256"))
                .alg(List.of("ES256"))
                .build());

        presentationDefinition = presentationDefinitionWithFields(
                id,
                List.of(
                        Field.builder().path(List.of("$.vct")).filter(Filter.builder().type("string").constDescriptor("SomeOtherVCTWeDontHave").build()).build(),
                        Field.builder().path(List.of("$.vct")).filter(Filter.builder().type("string").constDescriptor("defaultTestVCT").build()).build(),
                        Field.builder().path(List.of("$.last_name")).build(),
                        Field.builder().path(List.of("$.birthdate")).build()
                ),
                null,
                formats
        );

        managementEntity = managementEntity(id, presentationDefinition);

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);
        assertThrows(VerificationException.class, cred::verifyPresentation);
        // We will not have the failed state here, as this is set in th exception handling
    }

    @Test
    void testSDJWTIllegalSDClaim_thenFailure() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {

        sdJWTCredential = emulator.createIssuerAttackSDJWTMock();

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);
        assertThrows(VerificationException.class, cred::verifyPresentation);
        // We will not have the failed state here, as this is set in th exception handling
    }

    @Test
    void testSDJWTIllegalDisclosure_thenFailure() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {

        sdJWTCredential = emulator.createIllegalSDJWTMock();

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);
        assertThrows(VerificationException.class, cred::verifyPresentation);
        // We will not have the failed state here, as this is set in th exception handling
    }
    
    @Test
    void shouldVerifyWithInvalidIssuer_thenFail() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {
        managementEntity = managementEntity(id, presentationDefinition, "did:example:invalid-issuer");

        sdJWTCredential = emulator.createSDJWTMock();

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);
        VerificationException exception = assertThrows(VerificationException.class, cred::verifyPresentation);
        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals(VerificationErrorResponseCode.ISSUER_NOT_ACCEPTED, exception.getErrorResponseCode());
    }

    @Test
    void shouldVerifyWithUnresolvableIssuer_thenFail() throws NoSuchAlgorithmException, ParseException, JOSEException {

        sdJWTCredential = emulator.createSDJWTMock();

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenThrow(new DidResolverException("Could not resolve DID Document for issuer"));

        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);

        VerificationException exception = assertThrows(VerificationException.class, cred::verifyPresentation);
        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals(VerificationErrorResponseCode.PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE, exception.getErrorResponseCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"_sd", "..."})
    void shouldVerifyWithUnsupportedFormat_thenFail(String input) throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {

        HashMap<String, String> sdClaims = new HashMap<>();

        sdClaims.put(input, "Test forbidden claim");

        sdJWTCredential = emulator.createSDJWTMockWithClaims(sdClaims);

        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);

        VerificationException exception = assertThrows(VerificationException.class, cred::verifyPresentation);
        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"iss", "nbf", "exp", "cnf", "vct", "status", "iat"})
    void shouldVerifyWithUnsupportedClaims_thenFail(String input) throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {

        HashMap<String, String> sdClaims = new HashMap<>();

        sdClaims.put(input, "Test forbidden claim");

        sdJWTCredential = emulator.createSDJWTMockWithClaims(sdClaims);

        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);

        VerificationException exception = assertThrows(VerificationException.class, cred::verifyPresentation);
        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
    }

    @Test
    void noKeyBinding() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {

        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        vpToken = vpToken.substring(0, vpToken.lastIndexOf("~"));

        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);

        VerificationException exception = assertThrows(VerificationException.class, cred::verifyPresentation);
        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals(VerificationErrorResponseCode.HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
    }


    @Test
    void wrongKeyBindingFormat_thenFailure() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test", Instant.now().getEpochSecond(), "not-kb+jwt");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);

        VerificationException exception = assertThrows(VerificationException.class, cred::verifyPresentation);
        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals(VerificationErrorResponseCode.HOLDER_BINDING_MISMATCH, exception.getErrorResponseCode());
    }

    @Test
    void wrongKeyBindingNonce_thenFailure() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, UUID.randomUUID().toString(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);

        VerificationException exception = assertThrows(VerificationException.class, cred::verifyPresentation);
        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals(VerificationErrorResponseCode.MISSING_NONCE, exception.getErrorResponseCode());
    }

    @Test
    void invalidSDJwtIssuer_thenFailure() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {

        emulator = new SDJWTCredentialMock("", DEFAULT_KID_HEADER_VALUE);

        sdJWTCredential = emulator.createSDJWTMock();

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, UUID.randomUUID().toString(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);

        VerificationException exception = assertThrows(VerificationException.class, cred::verifyPresentation);
        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
    }

    @Test
    void invalidSDJwtKeyId_thenFailure() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException {

        emulator = new SDJWTCredentialMock(DEFAULT_ISSUER_ID, "");

        sdJWTCredential = emulator.createSDJWTMock();

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, UUID.randomUUID().toString(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper, verificationProperties);

        VerificationException exception = assertThrows(VerificationException.class, cred::verifyPresentation);
        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, exception.getErrorResponseCode());
    }
}