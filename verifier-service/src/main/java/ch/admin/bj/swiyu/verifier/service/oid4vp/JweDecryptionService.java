/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.jweutil.JweUtil;
import ch.admin.bj.swiyu.jweutil.JweUtilException;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationUnionDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.jwk.JWK;
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
 * <p>
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
            String jweString = verificationResponse.getResponse();
            String keyId = Optional.ofNullable(JWEObject.parse(jweString).getHeader().getKeyID())
                    .orElseThrow(() -> VerificationException.submissionError(
                            INVALID_REQUEST,
                            "Missing keyId. Unable to decrypt response."));
            JWK privateKey = resolvePrivateKey(managementEntity, keyId);
            String payload = JweUtil.decrypt(jweString, privateKey);
            return objectMapper.readValue(payload, VerificationPresentationUnionDto.class);
        } catch (ParseException e) {
            throw VerificationException.credentialError(e, "Failed to parse response.");
        } catch (JweUtilException e) {
            throw VerificationException.credentialError(e, "Response cannot be decrypted.");
        } catch (JsonProcessingException e) {
            throw VerificationException.credentialError(e, e.getOriginalMessage());
        }
    }

    @NotNull
    private static JWK resolvePrivateKey(Management managementEntity, String keyId)
            throws ParseException {
        ResponseSpecification responseSpecification = managementEntity.getResponseSpecification();
        JWKSet privateKeys = JWKSet.parse(Optional.ofNullable(responseSpecification.getJwksPrivate())
                // Throw illegal state, as this would be a server error
                .orElseThrow(() -> new IllegalStateException("Missing JWK private. Unable to decrypt response.")));
        return Optional.ofNullable(privateKeys.getKeyByKeyId(keyId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No matching JWK for keyId %s found. Unable to decrypt response.".formatted(keyId)));
    }
}
