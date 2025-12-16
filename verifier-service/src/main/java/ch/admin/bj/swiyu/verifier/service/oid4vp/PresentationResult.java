/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRejectionDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRequestDto;

/**
 * Result type that models the four mutually exclusive variants of an incoming
 * verification presentation in a type-safe way
 */
public sealed interface PresentationResult
        permits PresentationResult.Standard, PresentationResult.Dcql, PresentationResult.EncryptedDcql, PresentationResult.Rejection {

    /**
     * Standard / PE presentation (OID4VP ID2) with vp_token string and presentation_submission.
     */
    record Standard(VerificationPresentationRequestDto request) implements PresentationResult {
    }

    /**
     * DCQL presentation (OID4VP v1) with structured vp_token.
     */
    record Dcql(VerificationPresentationDCQLRequestDto request) implements PresentationResult {
    }

    /**
     * Encrypted DCQL presentation (OID4VP v1 with direct_post.jwt),
     * where the payload (after decryption) is already mapped to a DCQL request.
     */
    record EncryptedDcql(VerificationPresentationDCQLRequestDto request) implements PresentationResult {
    }

    /**
     * Rejection from the wallet / client.
     */
    record Rejection(VerificationPresentationRejectionDto request) implements PresentationResult {
    }
}
