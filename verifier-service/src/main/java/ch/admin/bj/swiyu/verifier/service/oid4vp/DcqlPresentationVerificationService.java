package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.dto.management.result.CredentialEvaluationDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.CredentialEvaluation;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.SdJwtVerificationResult;
import ch.admin.bj.swiyu.verifier.domain.VerificationResultData;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
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
    public VerificationResultData process(Management entity, VerificationPresentationDCQLRequestDto request) {
        var dcqlQuery = entity.getDcqlQuery();
        if (dcqlQuery == null) {
            // Happens when a verification request was created without a DCQL query (legacy format)
            // but the wallet nevertheless submits its presentation using the DCQL response format.
            throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "No DCQL query configured for this verification request");
        }
        List<DcqlCredential> requestedCredentials = dcqlQuery.getCredentials();
        Map<String, List<String>> vpTokens = request.getVpToken();
        if (vpTokens == null) {
            throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Missing vp_token object in presentation submission");
        }
        Map<String, List<Map<String, Object>>> verifiedResponses = new HashMap<>();
        Map<String, List<CredentialEvaluation>> evaluations = new HashMap<>();
        for (DcqlCredential requestedCredential : requestedCredentials) {
            String credentialRequestId = requestedCredential.getId();
            if (!vpTokens.containsKey(credentialRequestId)) {
                throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Missing vp token for requested credential id " + credentialRequestId);
            }
            List<SdJwtVerificationResult> verificationResults = getVerifiedTokenResults(entity, vpTokens.get(credentialRequestId),
            requestedCredential);
            evaluations.put(credentialRequestId, verificationResults.stream()
                .map(VerificationMapper::toCredentialEvaluation).toList());

            verifiedResponses.put(credentialRequestId, resolveDCQL(requestedCredential, verificationResults));
        }
        return VerificationResultData.builder()
            .vpTokens(vpTokens)
            .evaluations(evaluations)
            .verifiedResponses(verifiedResponses)
            .verifiedResponsesJsonString(writeAsString(verifiedResponses))
            .build();
    }

    /**
     * Resolved the DCQL Query on the verified credentials
     * @param requestedCredential the DCQL credential request query
     * @param verificationResults the verified wallet presentations
     * @return The claims requested in the DCQL Query
     */
    private List<Map<String, Object>> resolveDCQL(DcqlCredential requestedCredential, List<SdJwtVerificationResult> verificationResults) {
        List<SdJwt> sdJwts = verificationResults.stream().map(SdJwtVerificationResult::sdJwt).toList();
        sdJwts = dcqlEvaluator.filterByVct(sdJwts, requestedCredential.getMeta());

        if (sdJwts.isEmpty()) {
            throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "No matching SD-JWT for requested credential id " + requestedCredential.getId());
        }

        SdJwt sdjwt = sdJwts.getFirst(); // TODO EIDOMNI-887: Support for Claim Sets & credential sets
        dcqlEvaluator.validateRequestedClaims(sdjwt, requestedCredential.getClaims());
        return List.of(sdjwt.getResolvedClaims());
    }

    /**
     * Verifies the presented vpTokens for JWT Validity, SD-JWT Validity, Token Status List Validity and if it is trusted.
     * @param entity the verification request management entity
     * @param vpTokens the verifiable presentations of the wallet to be verified
     * @param requestedCredential the DCQL credential request
     * @return one {@link SdJwtVerificationResult} per given vpToken
     */
    private List<SdJwtVerificationResult> getVerifiedTokenResults(Management entity, List<String> vpTokens,
            DcqlCredential requestedCredential) {
        var requestedVpTokens = validatePresentedTokens(vpTokens, requestedCredential);

        List<SdJwtVerificationResult> verificationResults = requestedVpTokens.stream()
                .map(token -> presentationVerifier.verify(token, entity, requestedCredential))
                .toList();
        return verificationResults;
    }

    /**
     * Get the vpTokens from the received vp tokens that were requested.
     * @param vpTokens the vpTokens presented by the wallet
     * @param requestedCredential the DCQL defintion for the requested credential
     * @return a list of vpTokens that the verifier requested
     * @throws VerificationException if there is a serious issue with the presented vpTokens, 
     *         such as the requested vpToken is not present or too many tokens were sent which 
     *         would make processing take too long
     */
    private List<String> validatePresentedTokens(List<String> requestedVpTokens, DcqlCredential requestedCredential) {
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
        return requestedVpTokens;
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