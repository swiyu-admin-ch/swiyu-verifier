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
import ch.admin.bj.swiyu.verifier.service.trustregistry.TrustStatementInjectionService;
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
import java.util.Optional;
import java.util.UUID;


@Slf4j
@Service
@AllArgsConstructor
public class RequestObjectService {
    public static final String AUDIENCE = "https://self-issued.me/v2";
    public static final String RESPONSE_TYPE = "vp_token";

    private final ApplicationProperties applicationProperties;
    private final OpenIdClientMetadataConfiguration openIdClientMetadataConfiguration;
    private final ManagementRepository managementRepository;
    private final ObjectMapper objectMapper;
    private final JwtSigningService jwtSigningService;
    /**
     * Optional TP2.0 injection service. Present only when {@code swiyu.trust-registry.api-url} is configured.
     */
    private final Optional<TrustStatementInjectionService> trustStatementInjectionService;


    /**
     * Aggregated view of the effective configuration for a single request object.
     * It combines default application properties with potential per-management overrides.
     */
    private record EffectiveRequestObjectConfig(String externalUrl, String clientId, String verificationMethod) {
    }

    /**
     * Main entry point: build a JWT-Secured Authorization Request (JAR)
     * request object for the given management id
     * <p>
     * 1. Load and validate the Management entity (domain rules).
     * 2. Resolve the effective configuration (defaults + overrides).
     * 3. Build the request object DTO.
     * 4. Sign the request object
     */
    @Transactional(readOnly = true)
    public String assembleRequestObject(UUID managementEntityId) {

        log.debug("Prepare request object for mgmt-id {}", managementEntityId);

        log.trace("Load and validate the Management entity (domain rules)");
        Management managementEntity = loadAndValidateManagementEntity(managementEntityId);

        log.trace("Resolve the effective configuration (defaults + overrides).");
        var effectiveConfig = resolveEffectiveConfig(managementEntity);

        log.trace("Build the request object DTO (incl. optional TP2.0 verifier_info injection).");
        var requestObject = buildRequestObject(managementEntity, effectiveConfig, managementEntityId);

        log.trace("Sign and return the JWT string");
        return signRequestObject(requestObject, managementEntity, effectiveConfig);
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
        var dcqlQuery = managementEntity.getDcqlQuery();

        var clientMetadata = openIdClientMetadataConfiguration.getOpenIdClientMetadata();
        var clientMetadataBuilder = clientMetadata.toBuilder();

        ResponseSpecification responseSpecification = managementEntity.getResponseSpecification();
        // Enrich client metadata for DIRECT_POST_JWT response mode as required by the protocol
        if (ResponseModeType.DIRECT_POST_JWT.equals(responseSpecification.getResponseModeType())) {
            clientMetadataBuilder.jwks(ManagementMapper.toJWKSetDto(responseSpecification.getJwks()));
            clientMetadataBuilder.encryptedResponseEncValuesSupported(responseSpecification.getEncryptedResponseEncValuesSupported());
        }

        // Build a per-request copy of client_metadata so that per-verification overrides
        // (e.g. client_name, logo_uri, client_id) never mutate the global singleton map.
        var builtMetadata = clientMetadataBuilder.build();
        var overrides = managementEntity.getConfigurationOverride().clientMetadata();
        if (overrides != null && !overrides.isEmpty()) {
            var patchedProps = new java.util.HashMap<>(builtMetadata.getAdditionalProperties());
            patchedProps.putAll(overrides);
            builtMetadata.setAdditionalProperties(patchedProps);
        }

        var baseRequestObject = RequestObjectDto.builder()
                .audience(AUDIENCE)
                .nonce(managementEntity.getRequestNonce())
                .dcqlQuery(DcqlMapper.toDcqlQueryDto(dcqlQuery))
                .clientId(getClientId(effectiveConfig))
                .clientMetadata(builtMetadata)

                .responseType(RESPONSE_TYPE)
                .responseMode(ManagementMapper.toResponseModeDto(responseSpecification.getResponseModeType()))
                .responseUri(String.format("%s/oid4vp/api/request-object/%s/response-data",
                        effectiveConfig.externalUrl(),
                        managementEntityId))
                .state(managementEntity.getOauthState())
                .encryptedResponseEncValuesSupported(responseSpecification.getEncryptedResponseEncValuesSupported())
                .build();

        // Optional TP2.0 enrichment: when the trust-registry integration is enabled, inject the
        // verifier_info array (idTS + pvaTS). The clientId already resolved in effectiveConfig
        // doubles as the verifier DID looked up in the trust registry.
        return trustStatementInjectionService
                .map(svc -> svc.injectVerifierInfo(baseRequestObject, effectiveConfig.clientId(), managementEntity))
                .orElse(baseRequestObject);
    }

    private String getClientId(EffectiveRequestObjectConfig effectiveConfig) {
         if (StringUtils.isBlank(applicationProperties.getClientIdPrefix())) {
            return effectiveConfig.clientId();
         }

         return applicationProperties.getClientIdPrefix() + ":" + effectiveConfig.clientId();
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
        var externalUrl = override.externalUrlOrDefault(applicationProperties.getExternalUrl());
        var clientId = override.verifierDidOrDefault(applicationProperties.getClientId());
        var verificationMethod = override.verificationMethodOrDefault(applicationProperties.getSigningKeyVerificationMethod());

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

