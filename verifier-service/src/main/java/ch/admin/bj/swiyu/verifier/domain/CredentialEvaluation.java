package ch.admin.bj.swiyu.verifier.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;


/**
 * CredentialEvaluation JSON structure for persisting in the management table.
 */
// JSON-PERSISTED (ZDD): referenced from VerificationResultData, which is serialized to JSON in the "management" table.
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
    /**
     * Uses present data to establish if the VC should be suggested as valid. 
     * Allows {@link StatusVerificationResult} to be null when no status reference is part of the VC.
     * If no {@link IssuerTrustMarker} is present, will assume it is not trusted and thus not valid
     * @return true if the data indicate that the belonging VC may be regarded as valid without further inquiries
     */
    @JsonIgnore
    public boolean isValid() {
        // If no status reference was found counts as valid
        boolean isValidState = credentialStatus == null || credentialStatus.valid();
        // Trust markers must be present to be trusted
        boolean isTrusted = trustMarkers != null && trustMarkers.isTrusted();
        return isValidState && isTrusted;
    }
}

