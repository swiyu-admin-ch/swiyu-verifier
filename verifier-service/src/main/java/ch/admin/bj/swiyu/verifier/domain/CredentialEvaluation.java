package ch.admin.bj.swiyu.verifier.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;


/**
 * CredentialEvaluation
 */
@Schema(name = "CredentialEvaluation")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record CredentialEvaluation(
    @JsonProperty("credential_status")
    StatusVerificationResult credentialStatus,
    @JsonProperty("trust_markers")
    IssuerTrustMarker trustMarkers
){
    @JsonIgnoreProperties
    public boolean isValid() {
        // If no status reference was found counts as valid
        boolean isValidState = credentialStatus == null || credentialStatus.valid();
        boolean isTrusted = trustMarkers.isTrusted();
        return isValidState && isTrusted;
    }
}

