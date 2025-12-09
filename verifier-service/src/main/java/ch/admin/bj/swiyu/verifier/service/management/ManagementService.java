/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.api.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.api.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.api.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.domain.management.*;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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
        if (request.presentationDefinition() == null) {
            // Until the wallet is migrated we MUST provide a presentation definition.
            throw new IllegalArgumentException("PresentationDefinition must be provided");
        }

        var presentationDefinition = toPresentationDefinition(request.presentationDefinition());
        var dcqlQueryDto = request.dcqlQuery();
        if (dcqlQueryDto != null) {
            if (dcqlQueryDto.credentials().stream().anyMatch(cred -> Boolean.TRUE.equals(cred.multiple()))) {
                // Currently supporting only 1 vp token per credential query
                throw new IllegalArgumentException("multiple credentials in response for a single query not supported");
            }
            if (!CollectionUtils.isEmpty(dcqlQueryDto.credentialSets())) {
                // Not yet supporting credential sets
                throw new IllegalArgumentException("credential sets not yet supported");
            }
            if (dcqlQueryDto.credentials().stream().anyMatch(cred -> cred.meta().vctValues().isEmpty())) {
                throw new IllegalArgumentException("vct_values is required");
            }
        }
        var dcqlQuery = DcqlMapper.toDcqlQuery(dcqlQueryDto);
        List<TrustAnchor> trustAnchors = null;
        if (request.trustAnchors() != null) {
            trustAnchors = request.trustAnchors().stream().map(ManagementMapper::toTrustAnchor).toList();
        }

        var responseModeType = request.responseMode() == null ? ResponseModeType.DIRECT_POST : ManagementMapper.toResponseMode(request.responseMode());
        var responseSpecificationBuilder = ResponseSpecification.builder().responseModeType(responseModeType);
        if (ResponseModeTypeDto.DIRECT_POST_JWT.equals(request.responseMode())) {
            createEncryptionKeys(responseSpecificationBuilder);
        }

        var management = repository.save(Management.builder()
                .expirationInSeconds(applicationProperties.getVerificationTTL())
                .requestedPresentation(presentationDefinition)
                .dcqlQuery(dcqlQuery)
                .jwtSecuredAuthorizationRequest(requireNonNullElse(request.jwtSecuredAuthorizationRequest(), true))
                .responseSpecification(responseSpecificationBuilder.build())
                .acceptedIssuerDids(request.acceptedIssuerDids())
                .trustAnchors(trustAnchors)
                .configurationOverride(ManagementMapper.toSigningOverride(request.configuration_override()))
                .build()
                .resetExpiresAt());

        log.info("Created pending verification for id: {}", management.getId());
        return toManagementResponseDto(management, applicationProperties);
    }

    private static void createEncryptionKeys(ResponseSpecification.ResponseSpecificationBuilder responseSpecificationBuilder) {
        try {
            var ephemeralEncryptionKey = new ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate();
            JWKSet jwkSet = new JWKSet(ephemeralEncryptionKey);

            // Public keys used in request object
            responseSpecificationBuilder.jwks(jwkSet.toString(true));
            responseSpecificationBuilder.encryptedResponseEncValuesSupported(List.of("A128GCM"));
            // Private Keys used to unpack Encryption
            responseSpecificationBuilder.jwksPrivate(jwkSet.toString(false));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }


    @Transactional
    public void removeExpiredManagements() {
        log.info("Start scheduled removing of expired managements");
        repository.deleteByExpiresAtIsBefore(System.currentTimeMillis());
    }
}