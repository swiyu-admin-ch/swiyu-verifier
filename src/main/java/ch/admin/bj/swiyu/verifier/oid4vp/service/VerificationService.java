/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.service;

import java.util.Set;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationError.INVALID_REQUEST;
import static ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationErrorResponseCode.*;
import static ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationException.submissionError;
import static ch.admin.bj.swiyu.verifier.oid4vp.service.VerifiableCredentialExtractor.extractVerifiableCredential;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import ch.admin.bj.swiyu.verifier.oid4vp.api.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.oid4vp.api.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.oid4vp.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.ManagementEntity;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.ManagementEntityRepository;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist.StatusListReferenceFactory;
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
    private final ManagementEntityRepository managementEntityRepository;
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListReferenceFactory statusListReferenceFactory;
    private final ObjectMapper objectMapper;

    /**
     * Validates the presentation request. If it fails, it will
     * be marked as failed and can't be used anymore.
     *
     * @param managementEntityId the id of the ManagementEntity
     * @param request            the presentation request to verify
     */
    @Transactional(noRollbackFor = VerificationException.class)
    public void receiveVerificationPresentation(UUID managementEntityId, VerificationPresentationRequestDto request) {
        try {
            // 1. Check if the process is still pending and not expired
            var entity = getManagementEntity(managementEntityId);
            log.trace("Loaded management entity for {}", managementEntityId);
            verifyProcessNotClosed(entity);

            // 2. If the client / wallet aborted the verification -> mark as failed without throwing exception
            if (request.isClientRejection()) {
                markVerificationAsFailedDueToClientRejection(managementEntityId, request.getError_description());
                return;
            }

            // 3. verifiy the presentation submission
            log.trace("Starting submission verification for {}", managementEntityId);
            var credentialSubjectData = verifyPresentation(entity, request);
            log.trace("Submission verification completed for {}", managementEntityId);
            markVerificationAsSucceeded(managementEntityId, credentialSubjectData);
            log.trace("Saved successful verification result for {}", managementEntityId);
        } catch (VerificationException e) {
            markVerificationAsFailed(managementEntityId, e);
            log.trace("Saved failed verification result for {}", managementEntityId);
            throw e; // rethrow since client get notified of the error
        }
    }

    private static void verifyProcessNotClosed(ManagementEntity entity) {
        if (entity.isExpired() || !entity.isVerificationPending()) {
            throw submissionError(VERIFICATION_PROCESS_CLOSED);
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
            throw submissionError(VerificationErrorResponseCode.INVALID_PRESENTATION_DEFINITION,e.getMessage());
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

    private void markVerificationAsSucceeded(UUID managementEntityId, String credentialSubjectData) {
        var managementEntity = getManagementEntity(managementEntityId);
        managementEntity.verificationSucceeded(credentialSubjectData);
        managementEntityRepository.save(managementEntity);
    }

    private void markVerificationAsFailed(UUID managementEntityId, VerificationException e) {
        var managementEntity = managementEntityRepository.findById(managementEntityId);
        if (managementEntity.isPresent()) {
            var existing = managementEntity.get();
            existing.verificationFailed(e.getErrorType(), e.getErrorResponseCode());
            managementEntityRepository.save(existing);
        }
    }

    private void markVerificationAsFailedDueToClientRejection(UUID managementEntityId, String errorDescription) {
        var managementEntity = getManagementEntity(managementEntityId);
        managementEntity.verificationFailedDueToClientRejection(errorDescription);
        managementEntityRepository.save(managementEntity);
    }

    private String verifyPresentation(ManagementEntity entity, VerificationPresentationRequestDto request) {
        // NOTE: we do support invalid json of a presentation submissions, that is why we parse it manually.in case we have an
        // error we will update the entity (status to failed)
        var presentationSubmission = parseAndValidatePresentationSubmission(request.getPresentation_submission());
        if (isBlank(request.getVp_token()) || isNull(presentationSubmission)) {
            throw submissionError(AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM);
        }
        log.trace("Successfully verified presentation submission for id {}", entity.getId());
        return verifyPresentation(entity, request.getVp_token(), presentationSubmission);

    }

    private String verifyPresentation(ManagementEntity managementEntity, String vpToken, PresentationSubmissionDto presentationSubmission) {
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

    private ManagementEntity getManagementEntity(UUID managementEntityId) {
        return managementEntityRepository.findById(managementEntityId)
                .orElseThrow(() -> submissionError(AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND));
    }
}
