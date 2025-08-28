/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.api.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.api.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.service.management.ManagementMapper.toManagementResponseDto;
import static ch.admin.bj.swiyu.verifier.service.management.ManagementMapper.toPresentationDefinition;
import static java.util.Objects.requireNonNullElse;

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
        return toManagementResponseDto(management, applicationProperties);
    }

    @Transactional
    public ManagementResponseDto createVerificationManagement(CreateVerificationManagementDto request) {
        if (request == null) {
            throw new IllegalArgumentException("CreateVerificationManagement is null");
        }
        if (request.presentationDefinition() == null && request.dcqlQuery() == null) {
            throw new IllegalArgumentException("Either PresentationDefinition or DCQLQuery must be provided");
        }

        var presentationDefinition = toPresentationDefinition(request.presentationDefinition());
        var dcqlQuery = DcqlMapper.toDcqlQuery(request.dcqlQuery());
        List<TrustAnchor> trustAnchors = null;
        if (request.trustAnchors() != null) {
            trustAnchors = request.trustAnchors().stream().map(ManagementMapper::toTrustAnchor).toList();
        }

        var management = repository.save(Management.builder()
                .expirationInSeconds(applicationProperties.getVerificationTTL())
                .requestedPresentation(presentationDefinition)
                .dcqlQuery(dcqlQuery)
                .jwtSecuredAuthorizationRequest(requireNonNullElse(request.jwtSecuredAuthorizationRequest(), true))
                .acceptedIssuerDids(request.acceptedIssuerDids())
                .trustAnchors(trustAnchors)
                .configurationOverride(ManagementMapper.toSigningOverride(request.configuration_override()))
                .build()
                .resetExpiresAt());

        log.info("Created pending verification for id: {}", management.getId());
        return toManagementResponseDto(management, applicationProperties);
    }


    @Transactional
    public void removeExpiredManagements() {
        log.info("Start scheduled removing of expired managements");
        repository.deleteByExpiresAtIsBefore(System.currentTimeMillis());
    }
}