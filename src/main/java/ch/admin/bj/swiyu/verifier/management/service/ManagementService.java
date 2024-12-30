package ch.admin.bj.swiyu.verifier.management.service;

import ch.admin.bj.swiyu.verifier.management.api.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.management.api.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.management.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.management.domain.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.management.domain.management.Management;
import ch.admin.bj.swiyu.verifier.management.domain.management.ManagementRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.management.service.ManagementMapper.toManagementResponseDto;
import static ch.admin.bj.swiyu.verifier.management.service.ManagementMapper.toPresentationDefinition;

@Service
@AllArgsConstructor
@Slf4j
public class ManagementService {

    private final ManagementRepository repository;

    private final ApplicationProperties applicationProperties;

    @Transactional
    public ManagementResponseDto getManagement(UUID id) {
        log.debug("requested verification for id: {}", id);
        var management = repository.findById(id).orElseThrow(() -> new VerificationNotFoundException(id));
        if (management.isExpired()) {
            repository.deleteById(id);
            log.info("Deleted management for id since it is expired: {}", management.getId());
        }
        return toManagementResponseDto(management, applicationProperties.getOid4vpUrl());
    }

    @Transactional
    public ManagementResponseDto createVerificationManagement(CreateVerificationManagementDto request) {
        if (request == null) {
            throw new IllegalArgumentException("CreateVerificationManagement is null");
        }
        if (request.getPresentationDefinition() == null) {
            throw new IllegalArgumentException("PresentationDefinition is null");
        }

        var presentationDefinition = toPresentationDefinition(request.getPresentationDefinition());
        var management = repository.save(new Management(
                UUID.randomUUID(),
                applicationProperties.getVerificationTTL(),
                presentationDefinition,
                request.getJwtSecuredAuthorizationRequest() != null ? request.getJwtSecuredAuthorizationRequest() : true));

        log.info("Created pending verification for id: {}", management.getId());

        return toManagementResponseDto(management, applicationProperties.getOid4vpUrl());
    }

    @Transactional
    public void removeExpiredManagements() {
        log.info("Start scheduled removing of expired managements");
        repository.deleteByExpiresAtIsBefore(System.currentTimeMillis());
    }
}
