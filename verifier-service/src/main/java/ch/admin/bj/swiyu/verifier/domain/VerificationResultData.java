package ch.admin.bj.swiyu.verifier.domain;

import java.util.List;
import java.util.Map;

import lombok.Builder;

@Builder
public record VerificationResultData(
    Map<String, List<Map<String, Object>>> verifiedResponses,
    String verifiedResponsesJsonString, 
    Map<String, List<CredentialEvaluation>> evaluations,
    Map<String, List<String>> vpTokens) {
    
}
