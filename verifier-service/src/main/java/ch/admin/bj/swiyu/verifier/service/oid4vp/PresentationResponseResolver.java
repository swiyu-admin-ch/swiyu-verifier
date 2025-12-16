/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.api.VPApiVersion;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationUnionDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionError;

/**
 * Resolver that combines response mode handling, optional JWE decryption and
 * mapping of the resulting payload into a {@link PresentationResult}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PresentationResponseResolver {

    private final JweDecryptionService jweDecryptionService;

    /**
     * Resolves an incoming wallet response into a concrete {@code PresentationResult}.
     *
     * Processing rules:
     * <ul>
     *   <li>The response is first decrypted if required by the {@code Management}'s response mode
     *       (DIRECT_POST vs. DIRECT_POST_JWT). For DIRECT_POST the payload is used as-is; for
     *       DIRECT_POST_JWT the encrypted {@code response} field is decrypted.</li>
     *   <li>If the (decrypted) payload represents a rejection (error + error_description),
     *       a {@code PresentationResult.Rejection} is always returned. The API version does
     *       not influence rejection handling.</li>
     *   <li>For non-rejection responses, the API version controls how the payload is interpreted:
     *     <ul>
     *       <li>API version ID2 expects a standard/PE presentation with both {@code vp_token}
     *           and {@code presentation_submission} set. Missing fields cause a validation
     *           error with the "AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM" code.</li>
     *       <li>API version V1 expects a DCQL-based presentation with a structured
     *           {@code vp_token}. If the payload is still encrypted or the field
     *           combination is incomplete, the same validation error code is raised.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param managementEntity     management context providing response mode and keys
     * @param apiVersion           API version from the SWIYU-API-Version header
     * @param verificationResponse raw response sent by the wallet (possibly encrypted or a rejection)
     * @return the resolved {@code PresentationResult} (standard, DCQL, or rejection)
     * @throws IllegalArgumentException if response mode and payload (encrypted/plain) are inconsistent,
     *                                  or decryption fails
     */
    @Transactional(readOnly = true)
    public PresentationResult mapToPresentationResult(Management managementEntity,
                                                      VPApiVersion apiVersion,
                                                      VerificationPresentationUnionDto verificationResponse) {
        VerificationPresentationUnionDto payload = decryptIfNecessary(managementEntity, verificationResponse);

        // Rejection has priority, independent of the API version
        if (payload.isRejection()) {
            return new PresentationResult.Rejection(payload.toRejection());
        }

        return switch (apiVersion) {
            case ID2 -> mapId2Payload(payload);
            case V1 -> mapV1Payload(payload);
        };
    }

    private PresentationResult mapId2Payload(VerificationPresentationUnionDto payload) {
        if (!payload.isStandardPresentation()) {
            throw submissionError(
                    VerificationErrorResponseCode.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM,
                    "Incomplete submission for ID2, must contain vp_token and presentation_submission");
        }
        return new PresentationResult.Standard(payload.toStandardPresentation());
    }

    private PresentationResult mapV1Payload(VerificationPresentationUnionDto payload) {
        if (payload.isDcqlPresentation()) {
            return new PresentationResult.Dcql(payload.toDcqlPresentation());
        }
        if (payload.isEncryptedPresentation()) {
            // Should already be caught by decryptIfNecessary; here again for safety
            throw submissionError(
                    VerificationErrorResponseCode.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM,
                    "Encrypted DCQL presentation is not fully decrypted");
        }
        throw submissionError(
                VerificationErrorResponseCode.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM,
                "Incomplete submission, must contain only vp_token or response");
    }

    /**
     * Decrypts the verification response, if required by the verification request.
     * Else returns the verificationResponse unedited.
     */
    private VerificationPresentationUnionDto decryptIfNecessary(Management managementEntity,
                                                                VerificationPresentationUnionDto verificationResponse) {
        ResponseModeType responseModeType = managementEntity.getResponseSpecification().getResponseModeType();
        if (ResponseModeType.DIRECT_POST.equals(responseModeType)) {
            return verificationResponse;
        } else if (ResponseModeType.DIRECT_POST_JWT.equals(responseModeType) && verificationResponse.isEncryptedPresentation()) {
            return jweDecryptionService.decrypt(managementEntity, verificationResponse);
        } else if (ResponseModeType.DIRECT_POST_JWT.equals(responseModeType)) {
            // Lacking encryption although DIRECT_POST_JWT is configured
            throw new IllegalArgumentException("Lacking encryption. All elements of the response should be encrypted.");
        } else {
            throw new IllegalArgumentException("Unsupported response_mode: " + responseModeType);
        }
    }
}
