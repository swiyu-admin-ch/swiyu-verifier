/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationUnionDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseModeType;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import ch.admin.bj.swiyu.verifier.service.oid4vp.JweDecryptionService;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JweDecryptionServiceTest {

    private static ECKey ecKey;
    private static ObjectMapper objectMapper;

    private JweDecryptionService jweDecryptionService;

    @BeforeAll
    static void init() throws JOSEException {
        ecKey = new ECKeyGenerator(Curve.P_256).keyID("ad_hoc_generated_testkey").generate();
        objectMapper = new ObjectMapper();
    }

    @BeforeEach
    void setUp() {
        jweDecryptionService = new JweDecryptionService(objectMapper);
    }

    @Test
    void decrypt_whenValidJwe_thenReturnsParsedUnionDto() throws JOSEException {
        Management management = createTestManagementWithPrivateKey();

        String presentationId = "test_credential_id";
        List<String> presentationPayload = List.of("Not validated here");
        String claims = new JWTClaimsSet.Builder()
                .claim("vp_token", Map.of(presentationId, presentationPayload))
                .build()
                .toString();

        VerificationPresentationUnionDto encryptedUnion = VerificationPresentationUnionDto.builder()
                .response(jweEncrypt(claims, ecKey))
                .build();

        VerificationPresentationUnionDto decrypted = jweDecryptionService.decrypt(management, encryptedUnion);

        assertThat(decrypted.getVp_token()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> vpToken = (Map<String, List<String>>) decrypted.getVp_token();
        assertThat(vpToken).containsEntry(presentationId, presentationPayload);
    }

    @Test
    void decrypt_whenMissingKeyId_thenThrowsIllegalArgumentException() throws JOSEException {
        Management management = createTestManagementWithPrivateKey();

        String claims = new JWTClaimsSet.Builder().claim("foo", "bar").build().toString();
        String jweWithoutKeyId = jweEncryptWithoutKeyId(claims, ecKey);

        VerificationPresentationUnionDto encryptedUnion = VerificationPresentationUnionDto.builder()
                .response(jweWithoutKeyId)
                .build();

        VerificationException ex = assertThrows(VerificationException.class,
                () -> jweDecryptionService.decrypt(management, encryptedUnion));

        assertEquals("Missing keyId. Unable to decrypt response.", ex.getErrorDescription());
    }

    @Test
    void decrypt_whenDifferentKeyId_thenThrowsIllegalArgumentException() throws JOSEException {
        ECKey otherEcKey = new ECKeyGenerator(Curve.P_256).keyID("other-key-id").generate();
        Management management = createTestManagementWithPrivateKey();

        String claims = new JWTClaimsSet.Builder().claim("foo", "bar").build().toString();
        String jweWithOtherKeyId = jweEncrypt(claims, otherEcKey);

        VerificationPresentationUnionDto encryptedUnion = VerificationPresentationUnionDto.builder()
                .response(jweWithOtherKeyId)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jweDecryptionService.decrypt(management, encryptedUnion));

        assertEquals("No matching JWK for keyId other-key-id found. Unable to decrypt response.", ex.getMessage());
    }

    @Test
    void decrypt_whenSameKeyIdButDifferentKeyMaterial_thenThrowsIllegalArgumentException() throws JOSEException {
        // Same key ID but different key material than the one stored in management
        ECKey otherEcKey = new ECKeyGenerator(Curve.P_256).keyID("ad-hoc-generated-testkey").generate();
        Management management = createTestManagementWithPrivateKey();

        String claims = new JWTClaimsSet.Builder().claim("foo", "bar").build().toString();
        String jweWithDifferentMaterial = jweEncrypt(claims, otherEcKey);

        VerificationPresentationUnionDto encryptedUnion = VerificationPresentationUnionDto.builder()
                .response(jweWithDifferentMaterial)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jweDecryptionService.decrypt(management, encryptedUnion));

        assertThat(ex.getMessage()).contains("Unable to decrypt response");
    }

    private static Management createTestManagementWithPrivateKey() {
        JWKSet jwkSet = new JWKSet(ecKey);
        return Management.builder()
                .responseSpecification(
                        ResponseSpecification.builder()
                                // Only the private JWK set is needed for decryption
                                .jwksPrivate(jwkSet.toString(false))
                                .responseModeType(ResponseModeType.DIRECT_POST_JWT)
                                .build())
                .build();
    }

    private String jweEncrypt(String claims, ECKey key) throws JOSEException {
        JWEObject jweObject = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM)
                        .keyID(key.getKeyID())
                        .build(),
                new Payload(claims)
        );
        jweObject.encrypt(new ECDHEncrypter(key.toECPublicKey()));
        return jweObject.serialize();
    }

    private String jweEncryptWithoutKeyId(String claims, ECKey key) throws JOSEException {
        JWEObject jweObject = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM)
                        .build(),
                new Payload(claims)
        );
        jweObject.encrypt(new ECDHEncrypter(key.toECPublicKey()));
        return jweObject.serialize();
    }
}

