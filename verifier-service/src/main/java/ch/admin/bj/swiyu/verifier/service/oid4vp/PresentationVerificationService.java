package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.service.PresentationVerificationStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionError;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Parses and validates a presentation submission and delegates verification
 * to the appropriate format-specific strategy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Deprecated(since="OID4VP 1.0")
public class PresentationVerificationService {

    private final PresentationVerificationStrategyRegistry strategyRegistry;
    private final PresentationSubmissionService submissionService;

    /**
     * Verifies the given presentation request using the configured strategy.
     *
     * @param management the management entity defining verification context
     * @param request    the incoming presentation request DTO
     * @return credential subject data as a JSON string
     */
    public String verify(Management management, VerificationPresentationRequestDto request) {
        var presentationSubmission = submissionService.parseAndValidate(request.getPresentationSubmission());
        if (isBlank(request.getVpToken()) || isNull(presentationSubmission)) {
            throw submissionError(AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM, "Incomplete presentation submission received");
        }
        log.trace("Successfully verified presentation submission for id {}", management.getId());

        var descriptorMap = presentationSubmission.getDescriptorMap();
        if (descriptorMap == null || descriptorMap.isEmpty() || descriptorMap.getFirst() == null) {
            throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, "Descriptor map is empty or missing");
        }
        var format = descriptorMap.getFirst().getFormat();
        if (format == null || format.isEmpty()) {
            throw submissionError(VerificationErrorResponseCode.UNSUPPORTED_FORMAT, "Missing or empty format in presentation submission");
        }
        var strategy = strategyRegistry.getStrategy(format);
        return strategy.verify(request.getVpToken(), management, presentationSubmission);
    }
}

