/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */
package ch.admin.bj.swiyu.verifier.service.oid4vp.usecase;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.DcqlEvaluator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionError;

/**
 * Use case that processes and validates DCQL requests.
 * It verifies VP tokens into SD-JWTs, filters by VCT and validates requested claims.
 * Returns the extracted claims as a JSON string.
 */
@Component
@RequiredArgsConstructor
public class DcqlVerificationUseCase {

    private final PresentationVerifier<SdJwt> sdJwtPresentationVerifier;
    private final DcqlEvaluator dcqlEvaluator;
    private final ObjectMapper objectMapper;

    /**
     * Processes the DCQL presentation request and returns the extracted and validated claims as JSON.
     *
     * @throws VerificationException if validation or serialization fails.
     */
    public String process(Management entity, VerificationPresentationDCQLRequestDto request) {
        var requestedCredentials = entity.getDcqlQuery().getCredentials();
        var vpTokens = request.getVpToken();
        var verifiedResponses = new HashMap<String, List<Map<String, Object>>>();
        for (var requestedCredential : requestedCredentials) {
            if (!vpTokens.containsKey(requestedCredential.getId())) {
                throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Missing vp token for requested credential id " + requestedCredential.getId());
            }
            var requestedVpTokens = vpTokens.get(requestedCredential.getId());
            if (Boolean.FALSE.equals(requestedCredential.getMultiple()) && requestedVpTokens.size() > 1) {
                throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Expected only 1 vp token for " + requestedCredential.getId());
            }
            var sdJwts = requestedVpTokens.stream()
                    .map(token -> sdJwtPresentationVerifier.verify(token, entity))
                    .toList();
            sdJwts = dcqlEvaluator.filterByVct(sdJwts, requestedCredential.getMeta());
            if (sdJwts.isEmpty()) {
                throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "No matching SD-JWT for requested credential id " + requestedCredential.getId());
            }
            dcqlEvaluator.validateRequestedClaims(sdJwts.getFirst(), requestedCredential.getClaims());
            verifiedResponses.put(requestedCredential.getId(), extractClaimsList(sdJwts));
        }
        return writeAsString(verifiedResponses);
    }

    private List<Map<String, Object>> extractClaimsList(List<SdJwt> sdJwts) {
        return sdJwts.stream()
                .map(sdJwt -> sdJwt.getClaims().getClaims())
                .toList();
    }

    private String writeAsString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Failed to serialize object to string");
        }
    }
}
