/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.dto.requestobject.RequestObjectDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.util.json.JsonUtil;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseModeType;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import ch.admin.bj.swiyu.verifier.service.JwtSigningService;
import ch.admin.bj.swiyu.verifier.service.management.DcqlMapper;
import ch.admin.bj.swiyu.verifier.service.management.ManagementMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.service.oid4vp.RequestObjectMapper.toPresentationDefinitionDto;


@Slf4j
@Service
@AllArgsConstructor
public class RequestObjectService {
    public static final JWSAlgorithm USED_JWS_ALGORITHM = JWSAlgorithm.ES256;
    private final ApplicationProperties applicationProperties;
    private final OpenIdClientMetadataConfiguration openIdClientMetadataConfiguration;
    private final ManagementRepository managementRepository;
    private final ObjectMapper objectMapper;
    private final JwtSigningService jwtSigningService;

    /**
     * Aggregated view of the effective configuration for a single request object.
     * It combines default application properties with potential per-management overrides.
     */
    private record EffectiveRequestObjectConfig(String externalUrl, String clientId, String verificationMethod) {
    }

    /**
     * Main entry point: build a request object for the given management id and
     * optionally sign it depending on the management configuration.
     * <p>
     * 1. Load and validate the Management entity (domain rules).
     * 2. Resolve the effective configuration (defaults + overrides).
     * 3. Build the request object DTO.
     * 4. If signing is desired, sign and return the JWT string, otherwise return the DTO.
     */
    @Transactional(readOnly = true)
    public RequestObjectResult assembleRequestObject(UUID managementEntityId) {

        log.debug("Prepare request object for mgmt-id {}", managementEntityId);

        log.trace("Load and validate the Management entity (domain rules)");
        Management managementEntity = loadAndValidateManagementEntity(managementEntityId);

        log.trace("Resolve the effective configuration (defaults + overrides).");
        var effectiveConfig = resolveEffectiveConfig(managementEntity);

        log.trace("Build the request object DTO.");
        var requestObject = buildRequestObject(managementEntity, effectiveConfig, managementEntityId);

        log.trace("If signing is desired, sign and return the JWT string, otherwise return the DTO");
        if (isSigningRequested(managementEntity)) {
            String jwt = signRequestObject(requestObject, managementEntity, effectiveConfig);
            return new RequestObjectResult.Signed(jwt);
        } else {
            // if signing is not desired return the plain request object DTO
            return new RequestObjectResult.Unsigned(requestObject);
        }
    }

    /**
     * Decides whether the request object should be signed based on the
     * Management configuration flag.
     */
    private static boolean isSigningRequested(Management managementEntity) {
        return Boolean.TRUE.equals(managementEntity.getJwtSecuredAuthorizationRequest());
    }

    /**
     * Build the {@link RequestObjectDto} for the given management entity.
     * <p>
     * This method is responsible for mapping domain data (presentation definition,
     * DCQL query, response specification, etc.) into the wire-level request object.
     */
    private RequestObjectDto buildRequestObject(Management managementEntity,
                                                EffectiveRequestObjectConfig effectiveConfig,
                                                UUID managementEntityId) {
        var presentation = managementEntity.getRequestedPresentation();
        var dcqlQuery = managementEntity.getDcqlQuery();

        var clientMetadata = openIdClientMetadataConfiguration.getOpenIdClientMetadata();
        var clientMetadataBuilder = clientMetadata.toBuilder();

        ResponseSpecification responseSpecification = managementEntity.getResponseSpecification();
        // Enrich client metadata for DIRECT_POST_JWT response mode as required by the protocol
        if (ResponseModeType.DIRECT_POST_JWT.equals(responseSpecification.getResponseModeType())) {
            clientMetadataBuilder.jwks(ManagementMapper.toJWKSetDto(responseSpecification.getJwks()));
            clientMetadataBuilder.encryptedResponseEncValuesSupported(responseSpecification.getEncryptedResponseEncValuesSupported());
        }

        return RequestObjectDto.builder()
                .nonce(managementEntity.getRequestNonce())
                .version(applicationProperties.getRequestObjectVersion())
                .presentationDefinition(toPresentationDefinitionDto(presentation))
                .dcqlQuery(DcqlMapper.toDcqlQueryDto(dcqlQuery))
                .clientId(effectiveConfig.clientId())
                .clientMetadata(clientMetadataBuilder.build())
                .clientIdScheme(applicationProperties.getClientIdScheme())
                .responseType("vp_token")
                .responseMode(ManagementMapper.toResponseModeDto(responseSpecification.getResponseModeType()))
                .responseUri(String.format("%s/oid4vp/api/request-object/%s/response-data",
                        effectiveConfig.externalUrl(),
                        managementEntityId))
                .build();
    }

    /**
     * Sign the given request object and return the serialized JWT.
     * <p>
     * This includes:
     * - resolving the correct signer (override vs. default),
     * - validating that a signer is actually available,
     * - building the JWS header and JWT claims,
     * - performing the cryptographic signing and returning the compact serialization.
     */
    private String signRequestObject(RequestObjectDto requestObject,
                                     Management managementEntity,
                                     EffectiveRequestObjectConfig effectiveConfig) {
        var override = managementEntity.getConfigurationOverride();

        try {
            SignedJWT signedJwt = jwtSigningService.signJwt(createJWTClaimsSet(effectiveConfig.clientId(), requestObject),
                    override.keyId(), override.keyPin(), effectiveConfig.verificationMethod());
            return signedJwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign request object", e);
        }
    }

    /**
     * Resolve the effective configuration for a single management entity by
     * merging global application properties with per-management overrides.
     */
    private EffectiveRequestObjectConfig resolveEffectiveConfig(Management managementEntity) {
        var override = managementEntity.getConfigurationOverride();
        var externalUrl = StringUtils.isNotEmpty(override.externalUrl()) ? override.externalUrl() : applicationProperties.getExternalUrl();
        var clientId = StringUtils.isNotEmpty(override.verifierDid()) ? override.verifierDid() : applicationProperties.getClientId();
        var verificationMethod = StringUtils.isNotEmpty(override.verificationMethod()) ? override.verificationMethod() : applicationProperties.getSigningKeyVerificationMethod();
        return new EffectiveRequestObjectConfig(externalUrl, clientId, verificationMethod);
    }

    /**
     * Load the {@link Management} entity for the given id and ensure it is in a
     * valid state for verification (pending and not expired).
     */
    private Management loadAndValidateManagementEntity(UUID managementEntityId) {
        var managementEntity = managementRepository.findById(managementEntityId)
                .orElseThrow(() -> new NoSuchElementException("Verification Request with id " + managementEntityId + " not found"));

        if (!managementEntity.isVerificationPending()) {
            log.debug("Management with id {} is requested after already processing it", managementEntityId);
            throw new ProcessClosedException();
        }

        if (managementEntity.isExpired()) {
            log.info("Management with id {} was requested but is expired", managementEntityId);
            throw new NoSuchElementException("Verification Request with id " + managementEntityId + " is expired");
        }

        return managementEntity;
    }

    /**
     * Create a {@link JWTClaimsSet} from the request object by converting the DTO
     * to a Map and copying all non-null properties as JWT claims.
     */
    private JWTClaimsSet createJWTClaimsSet(String issuer, RequestObjectDto requestObject) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        builder.issuer(issuer);

        // Get all properties of the request object as a JSON-ready map
        Map<String, Object> requestObjectProperties = JsonUtil.getJsonObject(objectMapper.convertValue(requestObject, Map.class));
        requestObjectProperties.keySet().stream()
                // filter out null values
                .filter(key -> requestObjectProperties.get(key) != null)
                // add all non-null values to the JWT
                .forEach(key -> builder.claim(key, requestObjectProperties.get(key)));

        return builder.build();
    }

}

