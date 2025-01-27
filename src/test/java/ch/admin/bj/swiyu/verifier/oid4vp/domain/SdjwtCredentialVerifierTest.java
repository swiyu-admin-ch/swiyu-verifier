package ch.admin.bj.swiyu.verifier.oid4vp.domain;

import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey.DidResolverAdapter;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.eid.didresolver.DidResolveException;
import ch.admin.eid.didtoolbox.TrustDidWebException;
import com.authlete.sd.Disclosure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.*;

import static ch.admin.bj.swiyu.verifier.oid4vp.domain.management.PresentationDefinition.*;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.DidDocFixtures.issuerDidDoc;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.ManagementEntityFixtures.managementEntity;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.PresentationDefinitionFixtures.presentationDefinitionWithFields;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.PresentationDefinitionFixtures.sdwjtPresentationDefinition;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class SdjwtCredentialVerifierTest {
    @MockBean
    private DidResolverAdapter didResolverAdapter;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IssuerPublicKeyLoader issuerPublicKeyLoader;
    @MockBean
    private StatusListReferenceFactory statusListReferenceFactory;

    private UUID id;
    private String sdJWTCredential;
    private List<Disclosure> disclosures;
    private Jws<Claims> claims;

    @BeforeEach
    void setUp() throws Exception {

        // create a SD JWT Token
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        id = UUID.randomUUID();
        sdJWTCredential = emulator.createSDJWTMock();

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, "test-nonce", "test-test");
        var keyBinding = Arrays.stream(vpToken.split("~")).toList().getLast();
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
            // Verify Key Binding
            Map<String, Object> cnf = (Map<String, Object>) claims.getPayload().get("cnf");
            var signedKeyBinding = SignedJWT.parse(keyBinding);
            var holderBindingKey = JWK.parse(cnf);
            assertTrue(signedKeyBinding.verify(new ECDSAVerifier(holderBindingKey.toECKey())));

        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    @Test
    void testCompletenessOfSDJWTWithPresentationDefinition_thenSuccess() {

        var presentationDefinition = sdwjtPresentationDefinition(id, List.of("$.first_name", "$.last_name", "$.birthdate"));
        var managementEntity = managementEntity(id, presentationDefinition);
        var cred = new SdjwtCredentialVerifier(sdJWTCredential, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper);

        assertFalse(cred.checkPresentationDefinitionCriteria(claims.getPayload(), disclosures).isEmpty());
    }

    @Test
    void testCompletenessOfSDJWTWithPresentationDefinitionWithAdditionalDef_thenError() {

        var presentationDefinition = sdwjtPresentationDefinition(id, List.of("$.first_name", "$.last_name", "$.birthdate", "$.definitely_not_there"));
        var managementEntity = managementEntity(id, presentationDefinition);
        var cred = new SdjwtCredentialVerifier(sdJWTCredential, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper);
        var payload = claims.getPayload();

        assertThrows(VerificationException.class, () -> cred.checkPresentationDefinitionCriteria(payload, disclosures));
    }

    @Test
    void testCompletenessOfSDJWTWithPresentationDefinitionWithFilter_thenSuccess() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException, DidResolveException {
        // Form Credential Request
        HashMap<String, FormatAlgorithm> formats = new HashMap<>();
        formats.put("vc+sd-jwt", FormatAlgorithm.builder()
                .keyBindingAlg(List.of("ES256"))
                .alg(List.of("ES256"))
                .build());

        var presentationDefinition = presentationDefinitionWithFields(
                id,
                List.of(
                        Field.builder().path(List.of("$.vct")).filter(Filter.builder().type("string").constDescriptor(SDJWTCredentialMock.DEFAULT_VCT).build()).build(),
                        Field.builder().path(List.of("$.last_name")).build(),
                        Field.builder().path(List.of("$.birthdate")).build()
                ),
                null,
                formats
        );

        var managementEntity = managementEntity(id, presentationDefinition);

        // Create Default SDJWT Credential for presenting
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        id = UUID.randomUUID();
        sdJWTCredential = emulator.createSDJWTMock();

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper);
        cred.verifyPresentation();
    }

    @Test
    void testCompletenessOfSDJWTWithPresentationDefinitionWithFilterWrongVC_thenFailure() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException, DidResolveException {
        // Form Credential Request
        HashMap<String, FormatAlgorithm> formats = new HashMap<>();
        formats.put("vc+sd-jwt", FormatAlgorithm.builder()
                .keyBindingAlg(List.of("ES256"))
                .alg(List.of("ES256"))
                .build());

        var presentationDefinition = presentationDefinitionWithFields(
                id,
                List.of(
                        Field.builder().path(List.of("$.vct")).filter(Filter.builder().type("string").constDescriptor("SomeOtherVCTWeDontHave").build()).build(),
                        Field.builder().path(List.of("$.last_name")).build(),
                        Field.builder().path(List.of("$.birthdate")).build()
                ),
                null,
                formats
        );

        var managementEntity = managementEntity(id, presentationDefinition);

        // Create Default SDJWT Credential for presenting
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        id = UUID.randomUUID();
        sdJWTCredential = emulator.createSDJWTMock();

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper);
        assertThrows(VerificationException.class, cred::verifyPresentation);
        // We will not have the failed state here, as this is set in th exception handling
    }

    @Test
    void testSDJWTIllegalSDClaim_thenFailure() throws NoSuchAlgorithmException, ParseException, JOSEException, TrustDidWebException, DidResolveException {
        // Note: this test may fail in the future if the sd-jwt library is fixed.
        // The SD JWT has the claim set
        var presentationDefinition = sdwjtPresentationDefinition(id, List.of("$.first_name", "$.last_name", "$.birthdate"));
        var managementEntity = managementEntity(id, presentationDefinition);

        // Create Default SDJWT Credential for presenting
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        id = UUID.randomUUID();
        sdJWTCredential = emulator.createIssuerAttackSDJWTMock();

        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, managementEntity.getRequestNonce(), "test-test");

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        // For some reason we need to reapply the mockito override again, or else there is an error in the didtoolbox
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);

        assertEquals(VerificationStatus.PENDING, managementEntity.getState());
        var cred = new SdjwtCredentialVerifier(vpToken, managementEntity, issuerPublicKeyLoader, statusListReferenceFactory, objectMapper);
        assertThrows(VerificationException.class, cred::verifyPresentation);
        // We will not have the failed state here, as this is set in th exception handling
    }

}
