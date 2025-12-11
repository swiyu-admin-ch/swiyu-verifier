/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.api.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRejectionDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.common.doc.FlowDiagramAction;
import ch.admin.bj.swiyu.verifier.common.doc.FlowDiagramCondition;
import ch.admin.bj.swiyu.verifier.common.doc.FlowDiagramTerminal;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.service.callback.CallbackEventProducer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionError;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionErrorV1;

@Slf4j
@Service
@AllArgsConstructor
public class PresentationVerificationUsecase {

    private static final String LOADED_MANAGEMENT_ENTITY_FOR = "Loaded management entity for ";
    private static final String MANAGEMENT_ENTITY_NOT_FOUND = "Management entity not found: ";
    private final ManagementRepository managementEntityRepository;
    private final CallbackEventProducer callbackEventProducer;
    private final DcqlPresentationVerificationService dcqlPresentationVerificationService; // use case injection
    private final PresentationVerificationService presentationVerificationService;

    @FlowDiagramAction
    private static void verifyProcessNotClosed(Management entity) {
        if (entity.isExpired() || !entity.isVerificationPending()) {
            throw new ProcessClosedException();
        }
    }

    /**
     * Validates the presentation request for the standard OID4VP flow.
     * <p>
     * Behaviour on errors:
     * <ul>
     *   <li>On {@link VerificationException}, the management entity is marked as failed via
     *       {@code managementEntity.verificationFailed(...)} and the original exception is rethrown
     *       unchanged so that the REST layer can propagate the v2 error structure.</li>
     *   <li>In all cases (success or failure), a callback event is produced via
     *       {@link CallbackEventProducer#produceEvent(java.util.UUID)} to notify the business verifier
     *       that the verification attempt is finished.</li>
     * </ul>
     *
     * @param managementEntityId the id of the Management
     * @param request            the presentation request to verify
     */
    @Transactional(noRollbackFor = VerificationException.class, timeout = 60)
    @FlowDiagramTerminal
    // Timeout in case the verification process gets somewhere stuck, eg including fetching did document or status entries
    @Deprecated(since="OID4VP 1.0")
    public void receiveVerificationPresentation(UUID managementEntityId, VerificationPresentationRequestDto request) {
        log.debug("Received verification presentation for management id {}", managementEntityId);
        @FlowDiagramCondition(branch = "not OK", alternativeBranch = "proceed with main logic")
        @FlowDiagramAction("throw error")
        var managementEntity = managementEntityRepository.findById(managementEntityId)
            .orElseThrow(() -> submissionError(VerificationErrorResponseCode.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND, MANAGEMENT_ENTITY_NOT_FOUND + managementEntityId));
        try {
            // 1. Check if the process is still pending and not expired
            log.trace(LOADED_MANAGEMENT_ENTITY_FOR + "{}", managementEntityId);
            verifyProcessNotClosed(managementEntity);

            // 3. verifiy the presentation submission
            log.debug("Starting submission verification for {}", managementEntityId);
            @FlowDiagramAction
            var credentialSubjectData = presentationVerificationService.verify(managementEntity, request);
            log.trace("Submission verification completed for {}", managementEntityId);
            managementEntity.verificationSucceeded(credentialSubjectData);
            log.debug("Saved successful verification result for {}", managementEntityId);
        } catch (VerificationException e) {
            managementEntity.verificationFailed(e.getErrorResponseCode(), e.getErrorDescription());
            log.debug("Saved failed verification result for {}", managementEntityId);
            throw e; // rethrow since client get notified of the error
        } finally {
            // Notify Business Verifier that this verification is done
            callbackEventProducer.produceEvent(managementEntityId);
        }
    }

    /**
     * Validates the presentation request when the client / wallet rejects the verification.
     * <p>
     * Behaviour on errors:
     * <ul>
     *   <li>If the process is already closed (expired or not pending), a {@link ProcessClosedException}
     *       is thrown and the verification result is not changed.</li>
     *   <li>On {@link VerificationException} while marking the process as failed, the entity is updated
     *       via {@code managementEntity.verificationFailed(...)} and the exception is rethrown unchanged
     *       (v2 error structure).</li>
     *   <li>In the happy path, the management entity is marked as failed due to client rejection without
     *       throwing an exception to the caller.</li>
     *   <li>In all cases, a callback event is produced to signal completion.</li>
     * </ul>
     *
     * @param managementEntityId the id of the Management
     * @param request            the presentation rejection request from the client
     */
    @Transactional(noRollbackFor = VerificationException.class, timeout = 60)
    @FlowDiagramTerminal
    // Timeout in case the verification process gets somewhere stuck, eg including fetching did document or status entries
    public void receiveVerificationPresentationClientRejection(UUID managementEntityId, VerificationPresentationRejectionDto request) {
        log.debug("Received verification presentation for management id {}", managementEntityId);
        var managementEntity = managementEntityRepository.findById(managementEntityId)
            .orElseThrow(() -> submissionError(VerificationErrorResponseCode.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND, MANAGEMENT_ENTITY_NOT_FOUND + managementEntityId));
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
            callbackEventProducer.produceEvent(managementEntityId);
        }
    }

    /**
     * Validates the DCQL-based presentation request with VP token as object.
     * <p>
     * This method is used for the DCQL flow and differs from
     * {@link #receiveVerificationPresentation(UUID, VerificationPresentationRequestDto)} in how
     * {@link VerificationException} is propagated:
     * <ul>
     *   <li>On {@link VerificationException}, the management entity is marked as failed via
     *       {@code managementEntity.verificationFailed(...)}.</li>
     *   <li>The exception is then wrapped using {@link VerificationException#submissionErrorV1} to
     *       convert it into the legacy v1 error representation before being rethrown. This ensures
     *       backward-compatible error contracts for DCQL endpoints.</li>
     *   <li>In all cases (success or failure), a callback event is produced via
     *       {@link CallbackEventProducer#produceEvent(java.util.UUID)} to notify the business verifier
     *       that the DCQL verification attempt is finished.</li>
     * </ul>
     *
     * @param managementEntityId the id of the Management
     * @param request            the DCQL presentation request to verify
     */
    @FlowDiagramTerminal
    @Transactional(noRollbackFor = VerificationException.class, timeout = 60)
    public void receiveVerificationPresentationDCQL(UUID managementEntityId, VerificationPresentationDCQLRequestDto request) {
        log.debug("Received DCQL verification presentation for management id {}", managementEntityId);
        var managementEntity = managementEntityRepository.findById(managementEntityId)
            .orElseThrow(() -> submissionError(VerificationErrorResponseCode.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND, MANAGEMENT_ENTITY_NOT_FOUND + managementEntityId));
        try {
            // 1. Check if the process is still pending and not expired
            log.trace(LOADED_MANAGEMENT_ENTITY_FOR + "{}", managementEntityId);
            verifyProcessNotClosed(managementEntity);

            // 2. Verify the DCQL presentation submission
            log.debug("Starting DCQL submission verification for {}", managementEntityId);
            var credentialSubjectData = dcqlPresentationVerificationService.process(managementEntity, request);
            log.trace("DCQL submission verification completed for {}", managementEntityId);
            managementEntity.verificationSucceeded(credentialSubjectData);
            log.debug("Saved successful DCQL verification result for {}", managementEntityId);
        } catch (VerificationException e) {
            managementEntity.verificationFailed(e.getErrorResponseCode(), e.getErrorDescription());
            log.debug("Saved failed DCQL verification result for {}", managementEntityId);
            throw submissionErrorV1(e, e.getErrorResponseCode(), e.getErrorDescription()); // rethrow as v1
        } finally {
            // Notify Business Verifier that this verification is done
            callbackEventProducer.produceEvent(managementEntityId);
        }
    }
}
