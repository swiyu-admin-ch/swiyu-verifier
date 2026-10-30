package ch.admin.bj.swiyu.verifier.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

/**
 * Status of a credential as evaluated during the verification.
 *
 * @param valid           whether the status is considered valid
 * @param status          the raw status value read from the Status List Token
 * @param statusListToken the Status List Token used for the evaluation, exactly as fetched (compact JWS).
 *                        Only persisted if {@code application.additional-audit-information.status-list-token-enabled}
 *                        was enabled when the verification was completed.
 */
// JSON-PERSISTED (ZDD): referenced from CredentialEvaluation, which is serialized to JSON in the "management" table.
// The audit fields are nullable and omitted when null, so older rows stay readable and older instances ignore them.
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
public record StatusVerificationResult(
        boolean valid,
        Integer status,
        @JsonProperty("status_list_token")
        String statusListToken) {
}
