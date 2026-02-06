/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseModeType;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionError;
import static ch.admin.bj.swiyu.verifier.service.management.ManagementMapper.toManagementResponseDto;
import static ch.admin.bj.swiyu.verifier.service.management.ManagementMapper.toPresentationDefinition;

@Service
@AllArgsConstructor
@Slf4j
public class ManagementService {

    private final ApplicationProperties applicationProperties;

    private final ManagementTransactionalService managementTransactionalService;

    /**
     * Retrieves a management entity by its ID.
     * @param requestId the UUID of the management entity
     * @return the Management entity
     * @throws VerificationException if the entity is not found
     */
    public Management getManagementById(UUID requestId) {
        return managementTransactionalService.findById(requestId)
                .orElseThrow(() -> submissionError(VerificationErrorResponseCode.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND,
                        "Management entity not found: " + requestId));
    }

    /**
     * Retrieves and returns a management response DTO by ID, handling expiration.
     * @param id the UUID of the management
     * @return the ManagementResponseDto
     */
    public ManagementResponseDto getManagementResponseDto(UUID id) {
        log.debug("requested verification for id: {}", id);
        var management = managementTransactionalService.findAndHandleExpiration(id);
        return toManagementResponseDto(management, applicationProperties);
    }

    /**
     * Creates a new verification management based on the provided request.
     * @param request the DTO containing creation details
     * @return the ManagementResponseDto for the created management
     */
    public ManagementResponseDto createVerificationManagement(CreateVerificationManagementDto request) {
        CreateVerificationManagementValidator.validate(request);

        var presentationDefinition = toPresentationDefinition(request.presentationDefinition());
        var dcqlQuery = DcqlMapper.toDcqlQuery(request.dcqlQuery());
        var trustAnchors = ManagementMapper.toTrustAnchors(request.trustAnchors());
        var responseSpecificationBuilder = createResponseSpecificationBuilder(request.responseMode());

        var management = managementTransactionalService.saveNewManagement(
                presentationDefinition,
                dcqlQuery,
                request,
                trustAnchors,
                responseSpecificationBuilder
        );
        log.info("Created pending verification for id: {}", management.getId());
        return toManagementResponseDto(management, applicationProperties);
    }


    private ResponseSpecification.ResponseSpecificationBuilder createResponseSpecificationBuilder(ResponseModeTypeDto responseMode) {
        var responseModeType = responseMode == null ? ResponseModeType.DIRECT_POST : ManagementMapper.toResponseMode(responseMode);
        var responseSpecificationBuilder = ResponseSpecification.builder().responseModeType(responseModeType);
        if (ResponseModeTypeDto.DIRECT_POST_JWT.equals(responseMode)) {
            createEncryptionKeys(responseSpecificationBuilder);
        }
        return responseSpecificationBuilder;
    }

    private static void createEncryptionKeys(ResponseSpecification.ResponseSpecificationBuilder responseSpecificationBuilder) {
        try {
            var ephemeralEncryptionKey = new ECKeyGenerator(Curve.P_256)
                .keyID(UUID.randomUUID().toString())
                .algorithm(JWEAlgorithm.ECDH_ES)
                .generate();
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

    /**
     * Removes all expired managements from the system.
     */
    public void removeExpiredManagements() {
        managementTransactionalService.deleteExpiredManagements();
    }

    /**
     * Loads a management entity for update operations.
     * @param managementEntityId the UUID of the management entity
     * @return the Management entity
     */
    public Management loadManagementEntityForUpdate(UUID managementEntityId) {
        return managementTransactionalService.loadManagementEntityForUpdate(managementEntityId);
    }

    /**
     * Marks the verification as succeeded with the provided data.
     * @param managementEntityId the UUID of the management entity
     * @param credentialSubjectData the data from the credential subject
     */
    public void markVerificationSucceeded(UUID managementEntityId, String credentialSubjectData) {
        managementTransactionalService.markVerificationSucceeded(managementEntityId, credentialSubjectData);
    }

    /**
     * Marks the verification as failed with the provided exception.
     * @param managementEntityId the UUID of the management entity
     * @param e the VerificationException containing error details
     */
    public void markVerificationFailed(UUID managementEntityId, VerificationException e) {
        managementTransactionalService.markVerificationFailed(managementEntityId, e);
    }

    /**
     * Marks the verification as failed due to client rejection.
     * @param managementEntityId the UUID of the management entity
     * @param errorDescription the description of the error
     */
    public void markVerificationFailedDueToClientRejection(UUID managementEntityId, String errorDescription) {
        managementTransactionalService.markVerificationFailedDueToClientRejection(managementEntityId, errorDescription);
    }
}