/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */
package ch.admin.bj.swiyu.verifier.service.oid4vp;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionError;

/**
 * Application service that evaluates a DCQL presentation request.
 * <p>
 * For each requested credential it verifies VP tokens into {@link SdJwt}, filters by VCT,
 * validates the requested claims, and returns the extracted claims as a JSON string.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DcqlPresentationVerificationService {

    private final PresentationVerifier presentationVerifier;
    private final DcqlEvaluator dcqlEvaluator;
    private final ObjectMapper objectMapper;

    /**
     * Processes the DCQL presentation request and returns the validated claims per credential as JSON.
     * <p>
     * Throws a {@link VerificationException} with {@link VerificationErrorResponseCode#INVALID_PRESENTATION_SUBMISSION}
     * if required VP tokens are missing, do not match the DCQL constraints, or if serialization fails.
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
                    .map(token -> presentationVerifier.verify(token, entity, requestedCredential))
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
            log.error("Failed to serialize object to string. Message: {}", e.getMessage());


            throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Failed to serialize object to string"); // NOPMD - ExceptionAsFlowControl
        }
    }
}
