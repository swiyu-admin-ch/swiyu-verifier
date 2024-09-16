package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.mock.SDJWTCredentialMock;
import ch.admin.bit.eid.oid4vp.model.did.DidResolverAdapter;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.authlete.sd.Disclosure;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bit.eid.oid4vp.fixtures.DidDocFixtures.issuerDidDoc;
import static ch.admin.bit.eid.oid4vp.mock.CredentialSubmissionMock.getPresentationDefinitionMockWithFormat;
import static ch.admin.bit.eid.oid4vp.mock.ManagementEntityMock.getManagementEntityMock;
import static ch.admin.bit.eid.oid4vp.mock.PresentationDefinitionMocks.createPresentationDefinitionMock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class SDJWTCredentialTest {
    @MockBean
    private VerificationManagementRepository verificationManagementRepository;
    @MockBean
    private DidResolverAdapter didResolverAdapter;
    @Autowired
    private IssuerPublicKeyLoader issuerPublicKeyLoader;

    private UUID id;
    private String sdJWTCredential;
    private PresentationSubmission presentationSubmission;
    private List<Disclosure> disclosures;
    private Jws<Claims> claims;

    @BeforeEach
    void setUp() throws Exception {

        // create a SD JWT Token
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        id = UUID.randomUUID();
        sdJWTCredential = emulator.createSDJWTMock();

        presentationSubmission = getPresentationDefinitionMockWithFormat(1, false, "jwt_vc");
        var vpToken = emulator.addKeyBindingProof(sdJWTCredential, "test-nonce", "test-test");
        var keyBinding = Arrays.stream(vpToken.split("~")).toList().getLast();
        var parts = sdJWTCredential.split("~");
        disclosures = Arrays.stream(Arrays.copyOfRange(parts, 1, parts.length)).map(Disclosure::parse).toList();

        // lookup issuers public key
        var issuerDidDoc = issuerDidDoc(emulator.getIssuerId(), emulator.getKidHeaderValue());
        when(didResolverAdapter.resolveDid(emulator.getIssuerId())).thenReturn(issuerDidDoc);
        var publicKey = issuerPublicKeyLoader.loadPublicKey(sdJWTCredential);

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

        var presentationDefinition = createPresentationDefinitionMock(id, List.of("$.first_name", "$.last_name", "$.birthdate"));
        var managementEntity = getManagementEntityMock(id, presentationDefinition);
        var cred = new SDJWTCredential(sdJWTCredential, managementEntity, presentationSubmission, verificationManagementRepository, issuerPublicKeyLoader);

        assertFalse(cred.checkPresentationDefinitionCriteria(claims.getPayload(), disclosures).isEmpty());
    }

    @Test
    void testCompletenessOfSDJWTWithPresentationDefinitionWithAdditionalDef_thenError() {

        var presentationDefinition = createPresentationDefinitionMock(id, List.of("$.first_name", "$.last_name", "$.birthdate", "$.definitely_not_there"));
        var managementEntity = getManagementEntityMock(id, presentationDefinition);
        var cred = new SDJWTCredential(sdJWTCredential, managementEntity, presentationSubmission, verificationManagementRepository, issuerPublicKeyLoader);
        var payload = claims.getPayload();

        assertThrows(VerificationException.class, () -> cred.checkPresentationDefinitionCriteria(payload, disclosures));
    }
}
