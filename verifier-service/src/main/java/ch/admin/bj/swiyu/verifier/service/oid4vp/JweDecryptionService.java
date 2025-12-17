/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationUnionDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Optional;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationError.INVALID_REQUEST;

/**
 * Technical service responsible solely for JWE decryption of verification responses.
 *
 * It does not contain any business logic related to VP API versions or payload mapping.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JweDecryptionService {

    private final ObjectMapper objectMapper;

    public VerificationPresentationUnionDto decrypt(Management managementEntity,
                                                    VerificationPresentationUnionDto verificationResponse) {
        try {
            JWEObject jwe = JWEObject.parse(verificationResponse.getResponse());
            String keyId = Optional.ofNullable(jwe.getHeader().getKeyID())
                    .orElseThrow(() -> VerificationException.submissionError(
                            INVALID_REQUEST,
                            "Missing keyId. Unable to decrypt response."));
            JWEDecrypter decrypter = createJweDecryptor(managementEntity, keyId);
            jwe.decrypt(decrypter);
            return objectMapper.readValue(jwe.getPayload().toString(), VerificationPresentationUnionDto.class);
        } catch (ParseException | JOSEException | JsonProcessingException e) {
            throw VerificationException.credentialError(e, "Response cannot be decrypted.");
        }
    }

    @NotNull
    private static JWEDecrypter createJweDecryptor(Management managementEntity, String keyId)
            throws ParseException, JOSEException {
        ResponseSpecification responseSpecification = managementEntity.getResponseSpecification();
        JWKSet privateKeys = JWKSet.parse(Optional.ofNullable(responseSpecification.getJwksPrivate())
                // Throw illegal state, as this would be a server error
                .orElseThrow(() -> new IllegalStateException("Missing JWK private. Unable to decrypt response.")));
        ECKey privateKey = Optional.ofNullable(privateKeys.getKeyByKeyId(keyId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No matching JWK for keyId %s found. Unable to decrypt response.".formatted(keyId)))
                .toECKey();
        return new ECDHDecrypter(privateKey);
    }
}
