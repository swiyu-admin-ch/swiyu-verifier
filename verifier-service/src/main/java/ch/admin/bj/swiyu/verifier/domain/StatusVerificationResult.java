package ch.admin.bj.swiyu.verifier.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;

// JSON-PERSISTED (ZDD): referenced from CredentialEvaluation, which is serialized to JSON in the "management" table.
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record StatusVerificationResult(boolean valid, Integer status) {}