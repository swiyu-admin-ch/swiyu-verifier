package ch.admin.bit.eid.oid4vp;

import ch.admin.bit.eid.oid4vp.config.SDJWTConfiguration;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.mock.SDJWTCredentialMock;
import ch.admin.bit.eid.oid4vp.model.SDJWTCredential;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.authlete.sd.Disclosure;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static ch.admin.bit.eid.oid4vp.mock.CredentialSubmissionMock.getPresentationDefinitionMockWithFormat;
import static ch.admin.bit.eid.oid4vp.mock.ManagementEntityMock.getManagementEntityMock;
import static ch.admin.bit.eid.oid4vp.mock.PresentationDefinitionMocks.createPresentationDefinitionMock;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
class SDJWTCredentialTest {

    @Autowired
    private SDJWTConfiguration sdjwtConfiguration;

    @MockBean
    private VerificationManagementRepository verificationManagementRepository;

    private UUID id;
    private String sdJWTCredential;
    private PresentationSubmission presentationSubmission;
    private List<Disclosure> disclosures;
    private Jws<Claims> claims;

    @BeforeEach
    void setUp() throws Exception {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        PublicKey publicKey = loadPublicKey(sdjwtConfiguration);

        id = UUID.randomUUID();
        sdJWTCredential = emulator.createSDJWTMock(null, null, null);
        presentationSubmission = getPresentationDefinitionMockWithFormat(1, false, "jwt_vc");

        var parts = sdJWTCredential.split("~");
        disclosures = Arrays.stream(Arrays.copyOfRange(parts, 1, parts.length)).map(Disclosure::parse).toList();

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
    void testCompletenessOfSDJWTWIthPresentationDefinition_thenSuccess() {

        var presentationDefinition = createPresentationDefinitionMock(id, List.of("$.first_name", "$.last_name", "$.birthdate"));
        var managementEntity = getManagementEntityMock(id, presentationDefinition);
        var cred = new SDJWTCredential(sdJWTCredential, managementEntity, presentationSubmission, verificationManagementRepository, sdjwtConfiguration);

        assertTrue(cred.checkPresentationDefinitionCriteria(claims.getPayload(), disclosures));
    }

    @Test
    void testCompletenessOfSDJWTWIthPresentationDefinitionWithAdditionalDef_thenError() {

        var presentationDefinition = createPresentationDefinitionMock(id, List.of("$.first_name", "$.last_name", "$.birthdate", "$.definitely_not_there"));
        var managementEntity = getManagementEntityMock(id, presentationDefinition);
        var cred = new SDJWTCredential(sdJWTCredential, managementEntity, presentationSubmission, verificationManagementRepository, sdjwtConfiguration);

        assertThrows(VerificationException.class, () -> cred.checkPresentationDefinitionCriteria(claims.getPayload(), disclosures));
    }

    private PublicKey loadPublicKey(SDJWTConfiguration sdjwtConfig) {
        try {
            var sanitized = sdjwtConfig.getPublicKey().replace("\n", "").replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "");
            byte[] encoded = Base64.getDecoder().decode(sanitized);
            KeyFactory kf = KeyFactory.getInstance("EC");
            EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            return kf.generatePublic(keySpec);
        } catch (Exception e) {
            return null;
        }
    }
}
