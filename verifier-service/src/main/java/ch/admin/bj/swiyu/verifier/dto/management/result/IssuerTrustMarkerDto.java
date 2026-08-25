package ch.admin.bj.swiyu.verifier.dto.management.result;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Container for the trust‑markers relevant for verifiers establishing trust towards an issuer
 */
@Schema(name="IssuerTrustMarker")
@Builder
public record IssuerTrustMarkerDto (
    @JsonProperty("trust_method")
    TrustMethodDto trustMethod,
    @JsonProperty("is_trusted")
    @Schema(description = "Overall result of Trust Mark evaluation")
    boolean isTrusted,
    @JsonProperty("viTM")
    @Schema(description = "Verified Identity Trust Marker")
    boolean identityTrustMarker,
    @JsonProperty("caTM")
    @Schema(description = "Compliant Actor Trust Marker")
    boolean compliantActorTrustMarker,
    @JsonProperty("gucTM")
    @Schema(description = "Governed Use Case Trust Marker - is the VC governed?")
    boolean governedUseCaseTrustMarker,
    @JsonProperty("gucaTM")
    @Schema(description = "Governed Use Case Authorization Trust Marker - Does the Issuer have authorization for the governed use case?")
    boolean governedUseCaseAuthorizationTrustMarker
) {
}
