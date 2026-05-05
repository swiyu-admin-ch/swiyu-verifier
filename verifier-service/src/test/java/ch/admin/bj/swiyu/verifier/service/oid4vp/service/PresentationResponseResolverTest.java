package ch.admin.bj.swiyu.verifier.service.oid4vp.service;

import ch.admin.bj.swiyu.jweutil.JweUtil;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.dto.VPApiVersion;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationUnionDto;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseModeType;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import ch.admin.bj.swiyu.verifier.service.oid4vp.JweDecryptionService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.PresentationResponseResolver;
import ch.admin.bj.swiyu.verifier.service.oid4vp.PresentationResult;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PresentationResponseResolverTest {

    private static ECKey ecKey;
    private static ObjectMapper objectMapper;

    private PresentationResponseResolver presentationResponseResolver;

    @BeforeAll
    static void init() throws JOSEException {
        ecKey = new ECKeyGenerator(Curve.P_256).keyID("ad_hoc_generated_testkey").generate();
        objectMapper = new ObjectMapper();
    }

    @BeforeEach
    void setUp() {
        ApplicationProperties applicationProperties = mock(ApplicationProperties.class);
        when(applicationProperties.getMaxCompressedCipherTextLength()).thenReturn(100000);
        JweDecryptionService jweDecryptionService = new JweDecryptionService(objectMapper, applicationProperties);
        presentationResponseResolver = new PresentationResponseResolver(jweDecryptionService);
    }

    @Test
    void mapToPresentationResult_whenDirectPost_andRealDcqlToken_thenReturnsDcqlResult() {
        Management management = createTestManagement(ResponseModeType.DIRECT_POST);
        var realWorldJson = """
                {"vp_token":{"defaultTestDcqlCredentialId":["eyJraWQiOiJURVNUX0lTU1VFUl9JRCNrZXktMSIsInR5cCI6InZjK3NkLWp3dCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJURVNUX0lTU1VFUl9JRCIsIl9zZCI6WyJHbldFZ0JtVnRySEk3SnBFMWdZXzZGOUVDc2IxMnBpVUZDT2xseDQzOUFJIiwicklUNmxmNWY2ckc3UnpIa2NNT3JzdFJsbHh2SVJSMUwxSlR2ZFFVVUJKRSIsInZWelQtQXRCeHBISFZ4dUxfS0FYWUozR0hSS2p1Yk1pOGoyVVZ2Y2p6Zk0iXSwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6Ik5tQUM5Ym8xcVpOUVkyblpDSDlVTTJjXzJxMXV0R2JWNnY0RU1XSmRkNEUiLCJ5IjoibUxJbmRBa3JxNlE5Yi1kWTN0TVBUeVFZSEtDdGZYM0xBNGtxZmVwM2NqYyJ9fSwiaWF0IjoxNzcyNDQ3MjkwLCJ2Y3QiOiJkZWZhdWx0VGVzdFZDVCJ9.ZnyBCPjZkUMH13z4k48VAD755ZcuOcKaPqZ5GhkaKMuiKvZOD1fUXw9ESiEDoPkKzdHcHBqeT8D-mqXqt3I9zA~WyJMLVJwNnpvMDVtZkxJT1MyUE5DODdBIiwiYmlydGhkYXRlIiwiMTk0OS0wMS0yMiJd~WyI2dWFIYi00VzM4SWs0Vl9MUVlFZHlBIiwibGFzdF9uYW1lIiwiVGVzdExhc3ROYW1lIl0~WyJaU3ZvbG9yTjVPLVIwVjd6bkl5Qk9nIiwiZmlyc3RfbmFtZSIsIlRlc3RGaXJzdG5hbWUiXQ~eyJ0eXAiOiJrYitqd3QiLCJhbGciOiJFUzI1NiJ9.eyJzZF9oYXNoIjoiNHJPQzZPX3o4cS1qZDRnUHh5UUgyRUJYWjc0SkktNjZGYkROQmUtZ0F4TSIsImF1ZCI6ImRpZDpleGFtcGxlOjEyMzQ1IiwiaWF0IjoxNzcyNDQ3MjkwLCJub25jZSI6IlAydlo4REtBdFR1Q0lVMU03ZGFXTEE2NUd6b2E3NnRMIn0.-KDLpTlnktNs0w_w9IBy3EhNqICXVlsxUkrVt82ED1usxKVG8l71dn0l8AIIqhrrz9ynbDyim6seI0s3jpJ2YA"]}}""";

        VerificationPresentationUnionDto union = VerificationPresentationUnionDto.builder()
                .vp_token(realWorldJson)
                .build();

        PresentationResult result = assertDoesNotThrow(
                () -> presentationResponseResolver.mapToPresentationResult(management, VPApiVersion.V1, union));

        assertThat(result).isInstanceOf(PresentationResult.Dcql.class);
        assertThat(((PresentationResult.Dcql) result).request().getVpToken()).containsKey("defaultTestDcqlCredentialId");
    }


    /*
     * Verifies that when the response mode is DIRECT_POST_JWT and the VP token is encrypted (DCQL),
     * the {@link PresentationResponseResolver#mapToPresentationResult} method correctly decrypts
     * the payload and returns a {@link PresentationResult.Dcql} result.
     */
    @Test
    void mapToPresentationResult_whenDirectPostJWT_andDcqlVpTokenEncrypted_thenReturnsDcqlResult() {
        Management management = createTestManagement(ResponseModeType.DIRECT_POST_JWT);

        var realWorldJson = """
                {"vp_token":{"defaultTestDcqlCredentialId":["eyJraWQiOiJURVNUX0lTU1VFUl9JRCNrZXktMSIsInR5cCI6InZjK3NkLWp3dCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJURVNUX0lTU1VFUl9JRCIsIl9zZCI6WyJHbldFZ0JtVnRySEk3SnBFMWdZXzZGOUVDc2IxMnBpVUZDT2xseDQzOUFJIiwicklUNmxmNWY2ckc3UnpIa2NNT3JzdFJsbHh2SVJSMUwxSlR2ZFFVVUJKRSIsInZWelQtQXRCeHBISFZ4dUxfS0FYWUozR0hSS2p1Yk1pOGoyVVZ2Y2p6Zk0iXSwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6Ik5tQUM5Ym8xcVpOUVkyblpDSDlVTTJjXzJxMXV0R2JWNnY0RU1XSmRkNEUiLCJ5IjoibUxJbmRBa3JxNlE5Yi1kWTN0TVBUeVFZSEtDdGZYM0xBNGtxZmVwM2NqYyJ9fSwiaWF0IjoxNzcyNDQ3MjkwLCJ2Y3QiOiJkZWZhdWx0VGVzdFZDVCJ9.ZnyBCPjZkUMH13z4k48VAD755ZcuOcKaPqZ5GhkaKMuiKvZOD1fUXw9ESiEDoPkKzdHcHBqeT8D-mqXqt3I9zA~WyJMLVJwNnpvMDVtZkxJT1MyUE5DODdBIiwiYmlydGhkYXRlIiwiMTk0OS0wMS0yMiJd~WyI2dWFIYi00VzM4SWs0Vl9MUVlFZHlBIiwibGFzdF9uYW1lIiwiVGVzdExhc3ROYW1lIl0~WyJaU3ZvbG9yTjVPLVIwVjd6bkl5Qk9nIiwiZmlyc3RfbmFtZSIsIlRlc3RGaXJzdG5hbWUiXQ~eyJ0eXAiOiJrYitqd3QiLCJhbGciOiJFUzI1NiJ9.eyJzZF9oYXNoIjoiNHJPQzZPX3o4cS1qZDRnUHh5UUgyRUJYWjc0SkktNjZGYkROQmUtZ0F4TSIsImF1ZCI6ImRpZDpleGFtcGxlOjEyMzQ1IiwiaWF0IjoxNzcyNDQ3MjkwLCJub25jZSI6IlAydlo4REtBdFR1Q0lVMU03ZGFXTEE2NUd6b2E3NnRMIn0.-KDLpTlnktNs0w_w9IBy3EhNqICXVlsxUkrVt82ED1usxKVG8l71dn0l8AIIqhrrz9ynbDyim6seI0s3jpJ2YA"]}}""";

        String jweString = JweUtil.encrypt(realWorldJson, ecKey.toPublicJWK());

        VerificationPresentationUnionDto union = VerificationPresentationUnionDto.builder()
                .response(jweString)
                .build();

        PresentationResult result = assertDoesNotThrow(
                () -> presentationResponseResolver.mapToPresentationResult(management, VPApiVersion.V1, union));

        assertThat(result).isInstanceOf(PresentationResult.Dcql.class);
        assertThat(((PresentationResult.Dcql) result).request().getVpToken()).containsKey("defaultTestDcqlCredentialId");
    }


    @Test
    void mapToPresentationResult_whenDirectPostJwt_andEncryptedDcql_thenDecryptsAndReturnsDcqlResult() throws JOSEException {
        Management management = createTestManagement(ResponseModeType.DIRECT_POST_JWT);
        String presentationId = "test_credential_id";
        List<String> presentationPayload = List.of("Not validated here");
        String testClaims = new JWTClaimsSet.Builder()
                .claim("vp_token", Map.of(presentationId, presentationPayload))
                .build()
                .toString();

        VerificationPresentationUnionDto union = VerificationPresentationUnionDto.builder()
                .response(jweEncrypt(testClaims, ecKey))
                .build();

        PresentationResult result = assertDoesNotThrow(
                () -> presentationResponseResolver.mapToPresentationResult(management, VPApiVersion.V1, union));

        assertThat(result).isInstanceOf(PresentationResult.Dcql.class);
        var dcql = (PresentationResult.Dcql) result;
        assertThat(dcql.request().getVpToken()).containsEntry(presentationId, presentationPayload);
    }

    /**
     * When end-to-end encryption is required (DIRECT_POST_JWT), the wallet MUST only send data in encrypted form.
     * If clear text data is also sent, the encryption would be useless and we reject it.
     */
    @Test
    void mapToPresentationResult_whenDirectPostJwt_andDataRevealed_thenIllegalArgument() throws JOSEException {
        Management management = createTestManagement(ResponseModeType.DIRECT_POST_JWT);
        String presentationId = "test_credential_id";
        List<String> presentationPayload = List.of("Not validated here");
        String testClaims = new JWTClaimsSet.Builder()
                .claim("vp_token", Map.of(presentationId, presentationPayload))
                .build()
                .toString();

        VerificationPresentationUnionDto union = VerificationPresentationUnionDto.builder()
                .vp_token("plain-vp-token")
                .response(jweEncrypt(testClaims, ecKey))
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> presentationResponseResolver.mapToPresentationResult(management, VPApiVersion.V1, union));

        assertEquals("Lacking encryption. All elements of the response should be encrypted.", exception.getMessage());
    }

    @Test
    void mapToPresentationResult_whenDirectPostJwt_andDifferentKeyId_thenIllegalArgument() throws JOSEException {
        ECKey otherEcKey = new ECKeyGenerator(Curve.P_256).keyID("other-ad-hoc-generated-testkey").generate();
        Management management = createTestManagement(ResponseModeType.DIRECT_POST_JWT);
        String presentationId = "test_credential_id";
        List<String> presentationPayload = List.of("Not validated here");
        String testClaims = new JWTClaimsSet.Builder()
                .claim("vp_token", Map.of(presentationId, presentationPayload))
                .build()
                .toString();

        VerificationPresentationUnionDto union = VerificationPresentationUnionDto.builder()
                .response(jweEncrypt(testClaims, otherEcKey))
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> presentationResponseResolver.mapToPresentationResult(management, VPApiVersion.V1, union));

        assertEquals("No matching JWK for keyId other-ad-hoc-generated-testkey found. Unable to decrypt response.",
                exception.getMessage());
    }

    @Test
    void mapToPresentationResult_whenDirectPostJwt_andDifferentKeyMaterial_thenIllegalArgument() throws JOSEException {
        ECKey otherEcKey = new ECKeyGenerator(Curve.P_256).keyID("ad-hoc-generated-testkey").generate();
        Management management = createTestManagement(ResponseModeType.DIRECT_POST_JWT);
        String presentationId = "test_credential_id";
        List<String> presentationPayload = List.of("Not validated here");
        String testClaims = new JWTClaimsSet.Builder()
                .claim("vp_token", Map.of(presentationId, presentationPayload))
                .build()
                .toString();

        VerificationPresentationUnionDto union = VerificationPresentationUnionDto.builder()
                .response(jweEncrypt(testClaims, otherEcKey))
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> presentationResponseResolver.mapToPresentationResult(management, VPApiVersion.V1, union));

        assertThat(exception.getMessage()).contains("Unable to decrypt response");
    }

    private String jweEncrypt(String testClaims, ECKey ecKey) throws JOSEException {
        JWEObject jweObject = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM)
                        .keyID(ecKey.getKeyID())
                        .build(),
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