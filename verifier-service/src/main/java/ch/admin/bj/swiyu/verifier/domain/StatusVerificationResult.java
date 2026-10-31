package ch.admin.bj.swiyu.verifier.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

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
