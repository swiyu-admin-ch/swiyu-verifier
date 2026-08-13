package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.DcqlEvaluator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final ApplicationProperties applicationProperties;

    /**
     * Processes the DCQL presentation request and returns the validated claims per credential as JSON.
     * <p>
     * Throws a {@link VerificationException} with {@link VerificationErrorResponseCode#INVALID_PRESENTATION_SUBMISSION}
     * if required VP tokens are missing, {@code null}, contain {@code null} entries, do not match the DCQL
     * constraints, if serialization fails, if the {@code vp_token} object itself is missing/{@code null}, or if the
     * given {@link Management} entity has no DCQL query configured (e.g. a legacy verification request that
     * receives a DCQL-formatted wallet response).
     */
    public String process(Management entity, VerificationPresentationDCQLRequestDto request) {
        var dcqlQuery = entity.getDcqlQuery();
        if (dcqlQuery == null) {
            // Happens when a verification request was created without a DCQL query (legacy format)
            // but the wallet nevertheless submits its presentation using the DCQL response format.
            throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "No DCQL query configured for this verification request");
        }
        var requestedCredentials = dcqlQuery.getCredentials();
        var vpTokens = request.getVpToken();
        if (vpTokens == null) {
            throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Missing vp_token object in presentation submission");
        }
        var verifiedResponses = new HashMap<String, List<Map<String, Object>>>();
        for (var requestedCredential : requestedCredentials) {
            if (!vpTokens.containsKey(requestedCredential.getId())) {
                throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Missing vp token for requested credential id " + requestedCredential.getId());
            }
            var requestedVpTokens = vpTokens.get(requestedCredential.getId());
            if (requestedVpTokens == null) {
                throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Vp token entry for requested credential id " + requestedCredential.getId() + " must not be null");
            }
            if (!Boolean.TRUE.equals(requestedCredential.getMultiple()) && requestedVpTokens.size() > 1) {
                throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Expected only 1 vp token for " + requestedCredential.getId());
            }

            if (requestedVpTokens.size() > applicationProperties.getMaxVcsAccepted()) {
                throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Cannot Accept more than %s vcs received %s".formatted(applicationProperties.getMaxVcsAccepted(), requestedVpTokens.size()));
            }

            if (requestedVpTokens.stream().anyMatch(Objects::isNull)) {
                throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Vp token list for requested credential id " + requestedCredential.getId() + " must not contain null entries");
            }

            var sdJwts = requestedVpTokens.stream()
                    .map(token -> presentationVerifier.verify(token, entity, requestedCredential))
                    .toList();

            sdJwts = dcqlEvaluator.filterByVct(sdJwts, requestedCredential.getMeta());

            if (sdJwts.isEmpty()) {
                throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "No matching SD-JWT for requested credential id " + requestedCredential.getId());
            }

            var sdjwt = sdJwts.getFirst();
            dcqlEvaluator.validateRequestedClaims(sdjwt, requestedCredential.getClaims());
            verifiedResponses.put(requestedCredential.getId(), List.of(sdjwt.getResolvedClaims()));
        }
        return writeAsString(verifiedResponses);
    }

    private String writeAsString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JacksonException e) {
            log.error("Failed to serialize object to string. Message: {}", e.getMessage());
            throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Failed to serialize object to string"); // NOPMD - ExceptionAsFlowControl
        }
    }
}