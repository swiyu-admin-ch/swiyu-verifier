package ch.admin.bj.swiyu.verifier.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
// JSON-PERSISTED (ZDD): referenced from CredentialEvaluation, which is serialized to JSON in the "management" table.
public record IssuerTrustMarker(    
    TrustMethod trustMethod,
    boolean isTrusted,
    boolean identityTrustMarker,
    boolean compliantActorTrustMarker,
    boolean governedUseCaseTrustMarker,
    boolean governedUseCaseAuthorizationTrustMarker) {
}
