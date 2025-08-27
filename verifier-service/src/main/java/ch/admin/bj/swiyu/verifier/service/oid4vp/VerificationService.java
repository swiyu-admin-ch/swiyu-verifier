/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationDCQLRequestEncryptedDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRejectionDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.api.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.DcqlService;
import ch.admin.bj.swiyu.verifier.service.callback.WebhookService;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
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

import java.util.Set;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionError;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.VerifiableCredentialExtractor.extractVerifiableCredential;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
@Service
@AllArgsConstructor
public class VerificationService {

    private static final String LOADED_MANAGEMENT_ENTITY_FOR = "Loaded management entity for ";
    private final VerificationProperties verificationProperties;
    private final ManagementRepository managementEntityRepository;
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListReferenceFactory statusListReferenceFactory;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;
    private final SdJwtVerificationService sdJwtVerificationService;
    private final DcqlService dcqlService;

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
        log.debug("Received verification presentation for management id {}", managementEntityId);
        var managementEntity = managementEntityRepository.findById(managementEntityId).orElseThrow();
        try {
            // 1. Check if the process is still pending and not expired
            log.trace(LOADED_MANAGEMENT_ENTITY_FOR + "{}", managementEntityId);
            verifyProcessNotClosed(managementEntity);

            // 3. verifiy the presentation submission
            log.debug("Starting submission verification for {}", managementEntityId);
            var credentialSubjectData = verifyPresentation(managementEntity, request);
            log.trace("Submission verification completed for {}", managementEntityId);
            managementEntity.verificationSucceeded(credentialSubjectData);
            log.debug("Saved successful verification result for {}", managementEntityId);
        } catch (VerificationException e) {
            managementEntity.verificationFailed(e.getErrorResponseCode(), e.getErrorDescription());
            log.debug("Saved failed verification result for {}", managementEntityId);
            throw e; // rethrow since client get notified of the error
        } finally {
            // Notify Business Verifier that this verification is done
            webhookService.produceEvent(managementEntityId);
        }
    }

    /**
     * Validates the presentation request. If it fails, it will
     * be marked as failed and can't be used anymore.
     *
     * @param managementEntityId the id of the Management
     * @param request            the presentation request to verify
     */
    @Transactional(noRollbackFor = VerificationException.class, timeout = 60)
    // Timeout in case the verification process gets somewhere stuck, eg including fetching did document or status entries
    public void receiveVerificationPresentationClientRejection(UUID managementEntityId, VerificationPresentationRejectionDto request) {
        log.debug("Received verification presentation for management id {}", managementEntityId);
        var managementEntity = managementEntityRepository.findById(managementEntityId).orElseThrow();
        try {
            // 1. Check if the process is still pending and not expired
            log.trace(LOADED_MANAGEMENT_ENTITY_FOR + "{}", managementEntityId);
            verifyProcessNotClosed(managementEntity);
            // 2. If the client / wallet aborted the verification -> mark as failed without throwing exception
            managementEntity.verificationFailedDueToClientRejection(request.getErrorDescription());
        } catch (VerificationException e) {
            managementEntity.verificationFailed(e.getErrorResponseCode(), e.getErrorDescription());
            log.debug("Saved failed verification result for {}", managementEntityId);
            throw e; // rethrow since client get notified of the error
        } finally {
            // Notify Business Verifier that this verification is done
            webhookService.produceEvent(managementEntityId);
        }
    }

    /**
     * Validates the DCQL presentation request with VP token as object. If it fails, it will
     * be marked as failed and can't be used anymore.
     *
     * @param managementEntityId the id of the Management
     * @param request            the DCQL presentation request to verify
     */
    @Transactional(noRollbackFor = VerificationException.class, timeout = 60)
    public void receiveVerificationPresentationDCQL(UUID managementEntityId, VerificationPresentationDCQLRequestDto request) {
        log.debug("Received DCQL verification presentation for management id {}", managementEntityId);
        var managementEntity = managementEntityRepository.findById(managementEntityId).orElseThrow();
        try {
            // 1. Check if the process is still pending and not expired
            log.trace(LOADED_MANAGEMENT_ENTITY_FOR + "{}", managementEntityId);
            verifyProcessNotClosed(managementEntity);

            // 2. Verify the DCQL presentation submission
            log.debug("Starting DCQL submission verification for {}", managementEntityId);
            var credentialSubjectData = verifyDCQLPresentation(managementEntity, request);
            log.trace("DCQL submission verification completed for {}", managementEntityId);
            managementEntity.verificationSucceeded(credentialSubjectData);
            log.debug("Saved successful DCQL verification result for {}", managementEntityId);
        } catch (VerificationException e) {
            managementEntity.verificationFailed(e.getErrorResponseCode(), e.getErrorDescription());
            log.debug("Saved failed DCQL verification result for {}", managementEntityId);
            throw e; // rethrow since client get notified of the error
        } finally {
            // Notify Business Verifier that this verification is done
            webhookService.produceEvent(managementEntityId);
        }
    }

    /**
     * Validates the encrypted DCQL presentation request. If it fails, it will
     * be marked as failed and can't be used anymore.
     *
     * @param managementEntityId the id of the Management
     * @param request            the encrypted DCQL presentation request to verify
     */
    @Transactional(noRollbackFor = VerificationException.class, timeout = 60)
    public void receiveVerificationPresentationDCQLEncrypted(UUID managementEntityId, VerificationPresentationDCQLRequestEncryptedDto request) {
        log.debug("Received encrypted DCQL verification presentation for management id {}", managementEntityId);
        var managementEntity = managementEntityRepository.findById(managementEntityId).orElseThrow();
        try {
            // 1. Check if the process is still pending and not expired
            log.trace(LOADED_MANAGEMENT_ENTITY_FOR + "{}", managementEntityId);
            verifyProcessNotClosed(managementEntity);

            // 2. Verify the encrypted DCQL presentation submission
            log.debug("Starting encrypted DCQL submission verification for {}", managementEntityId);
            var credentialSubjectData = verifyEncryptedDCQLPresentation(managementEntity, request);
            log.trace("Encrypted DCQL submission verification completed for {}", managementEntityId);
            managementEntity.verificationSucceeded(credentialSubjectData);
            log.debug("Saved successful encrypted DCQL verification result for {}", managementEntityId);
        } catch (VerificationException e) {
            managementEntity.verificationFailed(e.getErrorResponseCode(), e.getErrorDescription());
            log.debug("Saved failed encrypted DCQL verification result for {}", managementEntityId);
            throw e; // rethrow since client get notified of the error
        } finally {
            // Notify Business Verifier that this verification is done
            webhookService.produceEvent(managementEntityId);
        }
    }

    private String verifyPresentation(Management entity, VerificationPresentationRequestDto request) {
        // NOTE: we do support invalid json of a presentation submissions, that is why we parse it manually.in case we have an
        // error we will update the entity (status to failed)
        var presentationSubmission = parseAndValidatePresentationSubmission(request.getPresentationSubmission());
        if (isBlank(request.getVpToken()) || isNull(presentationSubmission)) {
            throw submissionError(AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM, "Incomplete presentation submission received");
        }
        log.trace("Successfully verified presentation submission for id {}", entity.getId());
        return verifyPresentation(entity, request.getVpToken(), presentationSubmission);

    }

    private String verifyPresentation(Management managementEntity, String vpToken, PresentationSubmissionDto presentationSubmission) {
        // Note: we currently do not support multiple formats
        var format = presentationSubmission.getDescriptorMap().getFirst().getFormat();
        if (format == null || format.isEmpty()) {
            throw new IllegalArgumentException("No format " + format);
        }

        if (format.equals(SdjwtCredentialVerifier.CREDENTIAL_FORMAT)) {
            var credentialToBeProcessed = extractVerifiableCredential(vpToken, managementEntity, presentationSubmission);
            log.trace("Prepared VC for SD-JWT verification for id {}", managementEntity.getId());
            var verifier = new SdjwtCredentialVerifier(
                    credentialToBeProcessed,
                    managementEntity,
                    issuerPublicKeyLoader,
                    statusListReferenceFactory,
                    objectMapper,
                    verificationProperties);
            return verifier.verifyPresentation();
        }
        throw new IllegalArgumentException("Unknown format: " + format);
    }

    private String verifyDCQLPresentation(Management entity, VerificationPresentationDCQLRequestDto request) {
        var requestedCredentials = entity.getDcqlQuery().getCredentials();
        var vpTokens = request.getVpToken();
        for (var requestedCredential : requestedCredentials) {
            if (!vpTokens.containsKey(requestedCredential.getId())) {
                throw new IllegalArgumentException("Missing vp token for requested credential id " + requestedCredential.getId());
            }
            var requestedVpTokens = vpTokens.get(requestedCredential.getId());
            // We expect only 1 vpToken, but receive more than 1
            if (Boolean.FALSE.equals(requestedCredential.getMultiple()) && requestedVpTokens.size() > 1) {
                throw new IllegalArgumentException("Expected only 1 vp token for " + requestedCredential.getId());
            }
            var sdJwts = requestedVpTokens.stream().map(SdJwt::new).toList();
            sdJwts.forEach(sdjwt -> sdJwtVerificationService.verifyVpToken(sdjwt, entity));
            sdJwts = DcqlService.filterByVct(sdJwts, requestedCredential.getMeta());
            DcqlService.containsRequestedFields(sdJwts.getFirst(), requestedCredential.getClaims());
        }


        throw new IllegalArgumentException("DCQL verification functionality is not yet implemented. This feature will be available in a future release.");
    }

    private String verifyEncryptedDCQLPresentation(Management entity, VerificationPresentationDCQLRequestEncryptedDto request) {
        // TODO: Encrypted DCQL functionality is not yet implemented - this is a placeholder for the next ticket
        throw new IllegalArgumentException("Encrypted DCQL verification functionality is not yet implemented. This feature will be available in a future release.");
    }
}
