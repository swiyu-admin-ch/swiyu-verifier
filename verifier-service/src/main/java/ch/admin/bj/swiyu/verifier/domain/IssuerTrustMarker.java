package ch.admin.bj.swiyu.verifier.domain;

import lombok.Builder;

@Builder
public record IssuerTrustMarker(    
    TrustMethod trustMethod,
    boolean isTrusted,
    boolean identityTrustMarker,
    boolean compliantActorTrustMarker,
    boolean governedUseCaseTrustMarker,
    boolean governedUseCaseAuthorizationTrustMarker) {
}
