package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationUnionDto;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseModeType;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import ch.admin.bj.swiyu.verifier.service.oid4vp.DecryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecryptionServiceTest {

    private static ECKey ecKey;
    private static ObjectMapper objectMapper;
    private DecryptionService decryptionService;
    private ManagementRepository managementRepository;

    @BeforeAll
    static void init() throws JOSEException {
        ecKey = new ECKeyGenerator(Curve.P_256).keyID("ad-hoc-generated-testkey").generate();
        objectMapper = new ObjectMapper();
    }

    @BeforeEach
    void setUp() {
        managementRepository = mock(ManagementRepository.class);
        decryptionService = new DecryptionService(managementRepository, objectMapper);
    }

    @Test
    void testUnencryptdDecryption_whenDirectPost_thenSuccess() {
        Management management = createTestManagement(ResponseModeType.DIRECT_POST);
        when(managementRepository.findById(management.getId())).thenReturn(Optional.of(management));
        VerificationPresentationUnionDto presentation = VerificationPresentationUnionDto.builder().vp_token("Anything").build();
        VerificationPresentationUnionDto decrypted = assertDoesNotThrow(() ->  decryptionService.decrypt(management.getId(), presentation));
        assertEquals(presentation, decrypted);
    }

    /**
     * When sending a presentation with clear readable data and the same data also encrypted, we just ignore the encrypted data.
     * It is not good practise if done by the wallet, but no direct issue with the presentation.
     */
    @Test
    void testUnencryptdDecryption_whenDirectPost_whenUnecessaryEncryptionAdded_thenSuccess() throws JOSEException {
        Management management = createTestManagement(ResponseModeType.DIRECT_POST);
        when(managementRepository.findById(management.getId())).thenReturn(Optional.of(management));
        String presentationId = "test_credential_id";
        List<String> presentationPayload = List.of("Not validated here");
        String testClaims = new JWTClaimsSet.Builder().claim("vp_token", Map.of(presentationId, presentationPayload)).build().toString();
        VerificationPresentationUnionDto presentation = VerificationPresentationUnionDto.builder().vp_token("Anything").response(jweEncrypt(testClaims, ecKey)).build();
        VerificationPresentationUnionDto decrypted = assertDoesNotThrow(() ->  decryptionService.decrypt(management.getId(), presentation));
        assertEquals(presentation, decrypted);
    }

    @Test
    void testDecryption_thenSuccess() throws JOSEException {
        Management management = createTestManagement(ResponseModeType.DIRECT_POST_JWT);
        when(managementRepository.findById(management.getId())).thenReturn(Optional.of(management));
        String presentationId = "test_credential_id";
        List<String> presentationPayload = List.of("Not validated here");
        String testClaims = new JWTClaimsSet.Builder().claim("vp_token", Map.of(presentationId, presentationPayload)).build().toString();
        VerificationPresentationUnionDto presentation = VerificationPresentationUnionDto.builder().response(jweEncrypt(testClaims, ecKey)).build();
        VerificationPresentationUnionDto decrypted = assertDoesNotThrow(() ->  decryptionService.decrypt(management.getId(), presentation));
        assertThat( decrypted.getVp_token()).hasFieldOrPropertyWithValue(presentationId, presentationPayload);
    }


    /**
     * When requested End 2 End encryption, the wallet MUST only send the data in encrypted form.
     * If it is also sent clear text the encryption would be useless.
     */
    @Test
    void testDecryption_whenDataRevealed_thenIllegalArgument() throws JOSEException {
        Management management = createTestManagement(ResponseModeType.DIRECT_POST_JWT);
        UUID id = management.getId();
        when(managementRepository.findById(id)).thenReturn(Optional.of(management));
        String presentationId = "test_credential_id";
        List<String> presentationPayload = List.of("Not validated here");
        String testClaims = new JWTClaimsSet.Builder().claim("vp_token", Map.of(presentationId, presentationPayload)).build().toString();
        VerificationPresentationUnionDto presentation = VerificationPresentationUnionDto.builder().vp_token("Anything").response(jweEncrypt(testClaims, ecKey)).build();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->  decryptionService.decrypt(id, presentation));
        assertEquals("Lacking encryption. All elements of the response should be encrypted.", exception.getMessage()); // We want to tell the wallet developers why we refused the presentation
    }

    @Test
    void testDecryption_whenDifferentKeyId_thenIllegalArgument() throws JOSEException {
        ECKey otherEcKey = new ECKeyGenerator(Curve.P_256).keyID("other-ad-hoc-generated-testkey").generate();
        Management management = createTestManagement(ResponseModeType.DIRECT_POST_JWT);
        UUID id = management.getId();
        when(managementRepository.findById(id)).thenReturn(Optional.of(management));
        String presentationId = "test_credential_id";
        List<String> presentationPayload = List.of("Not validated here");
        String testClaims = new JWTClaimsSet.Builder().claim("vp_token", Map.of(presentationId, presentationPayload)).build().toString();
        VerificationPresentationUnionDto presentation = VerificationPresentationUnionDto.builder().response(jweEncrypt(testClaims, otherEcKey)).build();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->  decryptionService.decrypt(id, presentation));
        assertEquals("No matching JWK for keyId other-ad-hoc-generated-testkey found. Unable to decrypt response.", exception.getMessage()); // We want to tell the wallet developers why we refused the presentation
    }

    @Test
    void testDecryption_whenDifferentKey_thenIllegalArgument() throws JOSEException {
        ECKey otherEcKey = new ECKeyGenerator(Curve.P_256).keyID("ad-hoc-generated-testkey").generate();
        Management management = createTestManagement(ResponseModeType.DIRECT_POST_JWT);
        UUID id = management.getId();
        when(managementRepository.findById(id)).thenReturn(Optional.of(management));
        String presentationId = "test_credential_id";
        List<String> presentationPayload = List.of("Not validated here");
        String testClaims = new JWTClaimsSet.Builder().claim("vp_token", Map.of(presentationId, presentationPayload)).build().toString();
        VerificationPresentationUnionDto presentation = VerificationPresentationUnionDto.builder().response(jweEncrypt(testClaims, otherEcKey)).build();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->  decryptionService.decrypt(id, presentation));
        assertEquals("Response cannot be decrypted.", exception.getMessage()); // We want to tell the wallet developers why we refused the presentation
    }

    private String jweEncrypt(String testClaims, ECKey ecKey) throws JOSEException {
        JWEObject jweObject = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM).keyID(ecKey.getKeyID()).build(),
                new Payload(testClaims)
        );
        jweObject.encrypt(new ECDHEncrypter(ecKey.toECPublicKey()));
        return jweObject.serialize();
    }

    private static Management createTestManagement(ResponseModeType responseModeType) {
        return Management.builder()
                .responseSpecification(
                        ResponseSpecification.builder()
                                .responseModeType(responseModeType)
                                .jwksPrivate(new JWKSet(ecKey).toString(false))
                                .build())
                .build();
    }
}
