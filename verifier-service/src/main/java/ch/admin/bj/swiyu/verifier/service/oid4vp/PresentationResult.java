package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationRejectionDto;

/**
 * Result type that models the four mutually exclusive variants of an incoming
 * verification presentation in a type-safe way
 */
public sealed interface PresentationResult
        permits PresentationResult.Dcql, PresentationResult.Rejection {

    /**
     * DCQL presentation (OID4VP v1) with structured vp_token.
     */
    record Dcql(VerificationPresentationDCQLRequestDto request) implements PresentationResult {
    }

    /**
     * Rejection from the wallet / client.
     */
    record Rejection(VerificationPresentationRejectionDto request) implements PresentationResult {
    }
}
