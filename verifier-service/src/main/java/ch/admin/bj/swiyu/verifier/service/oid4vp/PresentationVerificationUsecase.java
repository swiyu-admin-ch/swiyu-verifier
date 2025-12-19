/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationRejectionDto;
import ch.admin.bj.swiyu.verifier.dto.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.service.callback.CallbackEventProducer;
import ch.admin.bj.swiyu.verifier.service.management.ManagementService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionErrorV1;

@Slf4j
@Service
@AllArgsConstructor
public class PresentationVerificationUsecase {

    private static final String LOADED_MANAGEMENT_ENTITY_FOR = "Loaded management entity for ";
    private final CallbackEventProducer callbackEventProducer;
    private final DcqlPresentationVerificationService dcqlPresentationVerificationService; // use case injection
    private final PresentationVerificationService presentationVerificationService;
    private final ManagementService managementService;


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
     *
     * @deprecated legacy methode for receiveVerificationPresentation
     */
    @Deprecated(since = "OID4VP 1.0")
    public void receiveVerificationPresentation(UUID managementEntityId, VerificationPresentationRequestDto request) {
        log.debug("Processing DIF presentation exchange presentation for request_id: {}", managementEntityId);
        // 1. Load management entity in a short transaction
        Management managementEntity = managementService.loadManagementEntityForUpdate(managementEntityId);
        try {
            // 2. Check if the process is still pending and not expired
            log.trace(LOADED_MANAGEMENT_ENTITY_FOR + "{}", managementEntityId);
            if (!managementEntity.isProcessStillOpen()) {
                throw new ProcessClosedException();
            }

            // 3. Perform the potentially long‑running remote verification outside of any DB transaction
            // verifiy the presentation submission
            log.debug("Starting submission verification for {}", managementEntityId);
            var credentialSubjectData = presentationVerificationService.verify(managementEntity, request);
            log.trace("Submission verification completed for {}", managementEntityId);

            // 4a. Persist successful verification result in a dedicated short transaction
            managementService.markVerificationSucceeded(managementEntityId, credentialSubjectData);
            log.debug("Saved successful verification result for {}", managementEntityId);
        } catch (VerificationException e) {
            // 4b. Persist failed verification result in a dedicated short transaction
            managementService.markVerificationFailed(managementEntityId, e);
            log.debug("Saved failed verification result for {}", managementEntityId);

            //PMD: rethrow since client gets notified of the error (v2 structure)
            throw e; // NOPMD - ExceptionAsFlowControl
        } finally {
            // 5. Notify Business Verifier that this verification is done (non-transactional)
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
    public void receiveVerificationPresentationClientRejection(UUID managementEntityId, VerificationPresentationRejectionDto request) {
        log.debug("Processing rejection for request_id: {}", managementEntityId);

        try {
            // 1. Mark as failed due to client rejection in its own short-lived transaction
            managementService.markVerificationFailedDueToClientRejection(managementEntityId, request.getErrorDescription());
        } catch (VerificationException e) {
            // 1a. Persist failed verification result in a dedicated short transaction
            managementService.markVerificationFailed(managementEntityId, e);
            log.debug("Saved failed verification result for {}", managementEntityId);


            //PMD: rethrow since client gets notified of the error (v2 structure)
            throw e; // NOPMD - ExceptionAsFlowControl
        } finally {
            // 2. Notify Business Verifier that this verification is done (non-transactional)
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
    public void receiveVerificationPresentationDCQL(UUID managementEntityId, VerificationPresentationDCQLRequestDto request) {
        log.debug("Processing DCQL presentation for request_id: {}", managementEntityId);
        // 1. Load management entity in a short transaction
        Management managementEntity = managementService.loadManagementEntityForUpdate(managementEntityId);
        try {
            // 2. Check if the process is still pending and not expired
            log.trace(LOADED_MANAGEMENT_ENTITY_FOR + "{}", managementEntityId);
            if (!managementEntity.isProcessStillOpen()) {
                throw new ProcessClosedException();
            }

            // 3. Perform the potentially long‑running remote/DCQL verification **outside** of any DB transaction
            log.debug("Starting DCQL submission verification for {}", managementEntityId);
            var credentialSubjectData = dcqlPresentationVerificationService.process(managementEntity, request);
            log.trace("DCQL submission verification completed for {}", managementEntityId);

            // 4. Persist successful verification result in a dedicated short transaction
            managementService.markVerificationSucceeded(managementEntityId, credentialSubjectData);
            log.debug("Saved successful DCQL verification result for {}", managementEntityId);
        } catch (VerificationException e) {
            // 4a. Persist failed verification result in a dedicated short transaction
            managementService.markVerificationFailed(managementEntityId, e);
            log.debug("Saved failed DCQL verification result for {}", managementEntityId);

            // PMD: we intentionally convert v2 -> v1 error contract here
            throw submissionErrorV1(e, e.getErrorResponseCode(), e.getErrorDescription()); // NOPMD - ExceptionAsFlowControl - rethrow as v1
        } finally {
            // 5. Notify Business Verifier that this verification is done.
            callbackEventProducer.produceEvent(managementEntityId);
        }
    }
}
