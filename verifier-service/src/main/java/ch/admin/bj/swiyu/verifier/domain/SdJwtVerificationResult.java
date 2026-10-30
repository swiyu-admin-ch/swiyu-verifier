package ch.admin.bj.swiyu.verifier.domain;

import java.util.Optional;

import ch.admin.bj.swiyu.sdjwtverifier.SdJwt;
import lombok.Builder;

/**
 * Result of verifying a single SD-JWT VP token.
 *
 * @param sdJwt                    the verified SD-JWT
 * @param trustMarkers             the trust evaluation of the issuer
 * @param statusVerificationResult the status check of the credential, including the Status List Token it is based on,
 *                                 or empty if the credential has no status reference
 */
@Builder
public record SdJwtVerificationResult(
    SdJwt sdJwt, 
    IssuerTrustMarker trustMarkers, 
    Optional<StatusVerificationResult> statusVerificationResult) {
}
