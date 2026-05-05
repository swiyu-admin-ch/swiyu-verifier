package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.domain.management.*;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionError;
import static java.util.Objects.requireNonNullElse;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagementTransactionalService {

    private static final String LOADED_MANAGEMENT_ENTITY_FOR = "Loaded management entity for ";
    private static final String MANAGEMENT_ENTITY_NOT_FOUND = "Management entity not found: ";

    private final ManagementRepository repository;
    private final ApplicationProperties applicationProperties;

    /**
     * Loads the Management and deletes it if expired, all within a single transaction.
     */
    @Transactional
    public Management findAndHandleExpiration(UUID id) {
        var management = repository.findById(id).orElseThrow(() -> new VerificationNotFoundException(id));
        if (management.isExpired()) {
            repository.deleteById(id);
            log.info("Deleted management for id since it is expired: {}", management.getId());
        }
        return management;
    }

    /**
     * Persists a new Management aggregate in its own transaction.
     */
    @Transactional
    public Management saveNewManagement(DcqlQuery dcqlQuery,
                                        CreateVerificationManagementDto request,
                                        List<TrustAnchor> trustAnchors,
                                        ResponseSpecification.ResponseSpecificationBuilder responseSpecificationBuilder) {
        return repository.save(Management.builder()
            .expirationInSeconds(applicationProperties.getVerificationTTL())
            .dcqlQuery(dcqlQuery)
            .jwtSecuredAuthorizationRequest(requireNonNullElse(request.jwtSecuredAuthorizationRequest(), true))
            .responseSpecification(responseSpecificationBuilder.build())
            .acceptedIssuerDids(request.acceptedIssuerDids())
            .trustAnchors(trustAnchors)
            .configurationOverride(ManagementMapper.toSigningOverride(request.configuration_override()))
            .build()
            .resetExpiresAt());
    }

    /**
     * Deletes all expired Managements in a single transactional operation.
     */
    @Transactional
    public void deleteExpiredManagements() {
        repository.deleteByExpiresAtIsBefore(System.currentTimeMillis());
    }


    /**
     * Claims a verification session for exclusive processing by transitioning its state
     * from {@link VerificationStatus#PENDING} to {@link VerificationStatus#IN_PROGRESS}.
     *
     * <p>Concurrency is handled via <b>optimistic locking</b>: both threads may load and
     * mutate the entity in memory, but only one can commit. Hibernate issues
     * {@code UPDATE … WHERE version = ?} at flush time — the losing thread receives an
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}, which the
     * caller must handle.</p>
     *
     * @param managementEntityId the session to claim
     * @return the {@link Management} entity in state {@code IN_PROGRESS}
     * @throws ProcessClosedException if the session is not {@code PENDING} or has expired
     * @throws EntityNotFoundException if no session exists for the given id
     * @throws org.springframework.orm.ObjectOptimisticLockingFailureException if a concurrent
     *         thread committed first
     */
    @Transactional(timeout = 10)
    public Management claimSessionForProcessing(UUID managementEntityId) {
        Management m = repository.findById(managementEntityId)
                .orElseThrow(() ->
                        submissionError(VerificationErrorResponseCode.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND,
                                MANAGEMENT_ENTITY_NOT_FOUND + managementEntityId)
                );
        if (m.isProcessStillOpen()) {
            // Mutate state — Hibernate detects the dirty field (optimistic locking)
            m.claimForProcessing();
        } else {
            log.warn("Submission rejected for session {}: current state={}, expired={}",
                    managementEntityId, m.getState(), m.isExpired());
            throw submissionError(VerificationErrorResponseCode.VERIFICATION_PROCESS_CLOSED,
                    "Submission rejected for session %s: current state=%s, expired=%s".formatted(
                    managementEntityId, m.getState(), m.isExpired()));
        }
        return m;
    }

    /**
     * Persists a successful verification result in its own short-lived transaction.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = VerificationException.class,
            timeout = 10
    )
    public void markVerificationSucceeded(UUID managementEntityId, String credentialSubjectData) {
        var managementEntity = repository.findById(managementEntityId)
            .orElseThrow(() -> submissionError(VerificationErrorResponseCode.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND,
                MANAGEMENT_ENTITY_NOT_FOUND + managementEntityId));
        managementEntity.verificationSucceeded(credentialSubjectData);
    }

    /**
     * Persists a failed verification result in its own short-lived transaction.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = VerificationException.class,
            timeout = 10
    )
    public void markVerificationFailed(UUID managementEntityId, VerificationException e) {
        var managementEntity = repository.findById(managementEntityId)
            .orElseThrow(() -> submissionError(VerificationErrorResponseCode.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND,
                MANAGEMENT_ENTITY_NOT_FOUND + managementEntityId));
        managementEntity.verificationFailed(e.getErrorResponseCode(), e.getErrorDescription());
    }

    /**
     * Persists a failed verification result due to an explicit client/wallet rejection
     * in its own short-lived transaction.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = VerificationException.class,
            timeout = 10
    )
    public void markVerificationFailedDueToClientRejection(UUID managementEntityId, String errorDescription) {
        var managementEntity = repository.findById(managementEntityId)
            .orElseThrow(() -> submissionError(VerificationErrorResponseCode.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND,
                MANAGEMENT_ENTITY_NOT_FOUND + managementEntityId));
        log.trace(LOADED_MANAGEMENT_ENTITY_FOR + "{}", managementEntityId);
        if (!managementEntity.isProcessStillOpen()) {
            throw new ProcessClosedException();
        }
        managementEntity.verificationFailedDueToClientRejection(errorDescription);
    }

    @Transactional
    public Optional<Management> findById(UUID requestId) {
        return repository.findById(requestId);
    }
}
