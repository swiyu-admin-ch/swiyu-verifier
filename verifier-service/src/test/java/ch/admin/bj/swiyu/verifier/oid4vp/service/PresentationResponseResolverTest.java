/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.service;

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
        JweDecryptionService jweDecryptionService = new JweDecryptionService(objectMapper);
        presentationResponseResolver = new PresentationResponseResolver(jweDecryptionService);
    }

    @Test
    void mapToPresentationResult_whenDirectPost_andUnencryptedStandard_thenReturnsStandardResult() {
        Management management = createTestManagement(ResponseModeType.DIRECT_POST);
        VerificationPresentationUnionDto union = VerificationPresentationUnionDto.builder()
                .vp_token("vp-token-jwt")
                .presentation_submission("{}")
                .build();

        PresentationResult result = assertDoesNotThrow(
                () -> presentationResponseResolver.mapToPresentationResult(management, VPApiVersion.ID2, union));

        assertThat(result).isInstanceOf(PresentationResult.PresentationExchange.class);
        var standard = (PresentationResult.PresentationExchange) result;
        assertEquals("vp-token-jwt", standard.request().getVpToken());
        assertEquals("{}", standard.request().getPresentationSubmission());
    }

    @Test
    void mapToPresentationResult_whenDirectPost_andUnnecessaryEncryption_thenUsesPlainData() throws JOSEException {
        Management management = createTestManagement(ResponseModeType.DIRECT_POST);
        String presentationId = "test_credential_id";
        List<String> presentationPayload = List.of("Not validated here");
        String testClaims = new JWTClaimsSet.Builder()
                .claim("vp_token", Map.of(presentationId, presentationPayload))
                .build()
                .toString();

        VerificationPresentationUnionDto union = VerificationPresentationUnionDto.builder()
                .vp_token("plain-vp-token")
                .response(jweEncrypt(testClaims, ecKey))
                .presentation_submission("{}")
                .build();

        PresentationResult result = assertDoesNotThrow(
                () -> presentationResponseResolver.mapToPresentationResult(management, VPApiVersion.ID2, union));

        assertThat(result).isInstanceOf(PresentationResult.PresentationExchange.class);
        var standard = (PresentationResult.PresentationExchange) result;
        assertEquals("plain-vp-token", standard.request().getVpToken());
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
