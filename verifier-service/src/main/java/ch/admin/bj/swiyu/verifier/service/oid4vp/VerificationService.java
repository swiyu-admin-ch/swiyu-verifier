/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp;

import java.util.Set;
import java.util.UUID;


import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionError;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.VerifiableCredentialExtractor.extractVerifiableCredential;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.api.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.service.callback.WebhookService;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
public class VerificationService {

    private final VerificationProperties verificationProperties;
    private final ManagementRepository managementEntityRepository;
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListReferenceFactory statusListReferenceFactory;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;

    /**
     * Validates the presentation request. If it fails, it will
     * be marked as failed and can't be used anymore.
     *
     * @param managementEntityId the id of the Management
     * @param request            the presentation request to verify
     */
    @Transactional(noRollbackFor = VerificationException.class, timeout = 60)
    // Timeout in case the verification process gets somewhere stuck, eg including fetching did document or status entries
    public void receiveVerificationPresentation(UUID managementEntityId, VerificationPresentationRequestDto request) {
        var managementEntity = managementEntityRepository.findById(managementEntityId).orElseThrow();
        try {
            // 1. Check if the process is still pending and not expired
            log.trace("Loaded management entity for {}", managementEntityId);
            verifyProcessNotClosed(managementEntity);

            // 2. If the client / wallet aborted the verification -> mark as failed without throwing exception
            if (request.isClientRejection()) {
                managementEntity.verificationFailedDueToClientRejection(request.getError_description());
                return;
            }

            // 3. verifiy the presentation submission
            log.trace("Starting submission verification for {}", managementEntityId);
            var credentialSubjectData = verifyPresentation(managementEntity, request);
            log.trace("Submission verification completed for {}", managementEntityId);
            managementEntity.verificationSucceeded(credentialSubjectData);
            log.trace("Saved successful verification result for {}", managementEntityId);
        } catch (VerificationException e) {
            managementEntity.verificationFailed(e.getErrorResponseCode(), e.getErrorDescription());
            log.trace("Saved failed verification result for {}", managementEntityId);
            throw e; // rethrow since client get notified of the error
        } finally {
            // Notify Business Verifier that this verification is done
            webhookService.produceEvent(managementEntityId);
        }
    }


    private static void verifyProcessNotClosed(Management entity) {
        if (entity.isExpired() || !entity.isVerificationPending()) {
            throw new ProcessClosedException();
        }
    }

    private static PresentationSubmissionDto parseAndValidatePresentationSubmission(String presentationSubmissionJson) {
        if (isBlank(presentationSubmissionJson)) {
            return null;
        }

        var objectMapper = new ObjectMapper();
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        try {
            var presentationSubmission = objectMapper.readValue(presentationSubmissionJson, PresentationSubmissionDto.class);
            validatePresentationSubmission(presentationSubmission);
            return presentationSubmission;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, e.getMessage());
        }

    }

    private static void validatePresentationSubmission(PresentationSubmissionDto presentationSubmission) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<PresentationSubmissionDto>> violations = validator.validate(presentationSubmission);

            if (violations.isEmpty()) {
                return;
            }

            StringBuilder builder = new StringBuilder();
            for (ConstraintViolation<PresentationSubmissionDto> violation : violations) {
                builder.append("%s%s - %s".formatted(builder.isEmpty() ? "" : ", ", violation.getPropertyPath(), violation.getMessage()));
            }
            throw new IllegalArgumentException("Invalid presentation submission: " + builder);
        }
    }

    private String verifyPresentation(Management entity, VerificationPresentationRequestDto request) {
        // NOTE: we do support invalid json of a presentation submissions, that is why we parse it manually.in case we have an
        // error we will update the entity (status to failed)
        var presentationSubmission = parseAndValidatePresentationSubmission(request.getPresentation_submission());
        if (isBlank(request.getVp_token()) || isNull(presentationSubmission)) {
            throw submissionError(AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM, "Incomplete presentation submission received");
        }
        log.trace("Successfully verified presentation submission for id {}", entity.getId());
        return verifyPresentation(entity, request.getVp_token(), presentationSubmission);

    }

    private String verifyPresentation(Management managementEntity, String vpToken, PresentationSubmissionDto presentationSubmission) {
        // Note: we currently do not support multiple formats
        var format = presentationSubmission.getDescriptorMap().getFirst().getFormat();
        if (format == null || format.isEmpty()) {
            throw new IllegalArgumentException("No format " + format);
        }

        switch (format) {
            case SdjwtCredentialVerifier.CREDENTIAL_FORMAT -> {
                var credentialToBeProcessed = extractVerifiableCredential(vpToken, managementEntity, presentationSubmission);
                log.trace("Prepared VC for verification for id {}", managementEntity.getId());
                var verifier = new SdjwtCredentialVerifier(
                        credentialToBeProcessed,
                        managementEntity,
                        issuerPublicKeyLoader,
                        statusListReferenceFactory,
                        objectMapper,
                        verificationProperties);
                return verifier.verifyPresentation();
            }
            default -> throw new IllegalArgumentException("Unknown format: " + format);
        }
    }
}