package ch.admin.bj.swiyu.verifier.domain;

import java.util.Optional;

import ch.admin.bj.swiyu.sdjwtverifier.SdJwt;
import lombok.Builder;

@Builder
public record SdJwtVerificationResult(
    SdJwt sdJwt, 
    IssuerTrustMarker trustMarkers, 
    Optional<StatusVerificationResult> statusVerificationResult) {
}
