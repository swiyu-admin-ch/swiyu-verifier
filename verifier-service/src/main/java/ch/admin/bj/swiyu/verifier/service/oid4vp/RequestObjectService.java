/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.api.requestobject.RequestObjectDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.SignerProvider;
import ch.admin.bj.swiyu.verifier.common.exception.ProcessClosedException;
import ch.admin.bj.swiyu.verifier.common.json.JsonUtil;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import ch.admin.bj.swiyu.verifier.service.SignatureService;
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
    private final SignatureService signatureService;

    @Transactional(readOnly = true)
    public Object assembleRequestObject(UUID managementEntityId) {

        log.debug("Prepare request object for mgmt-id {}", managementEntityId);

        var managementEntity = managementRepository.findById(managementEntityId)
                .orElseThrow();

        if (!managementEntity.isVerificationPending()) {
            log.debug("Management with id {} is requested after already processing it", managementEntityId);
            throw new ProcessClosedException();
        }

        if (managementEntity.isExpired()) {
            log.info("Management with id {} was requested but is expired", managementEntityId);
            throw new NoSuchElementException("Verification Request with id " + managementEntityId + " is expired");
        }

        var presentation = managementEntity.getRequestedPresentation();


        // Override Params
        var override = managementEntity.getConfigurationOverride();
        var externalUrl = StringUtils.isNotEmpty(override.externalUrl()) ? override.externalUrl() : applicationProperties.getClientId();
        var clientId = StringUtils.isNotEmpty(override.verifierDid()) ? override.verifierDid() : applicationProperties.getClientId();
        var verificationMethod = StringUtils.isNotEmpty(override.verificationMethod()) ? override.verificationMethod() : applicationProperties.getSigningKeyVerificationMethod();
        var requestObject = RequestObjectDto.builder()
                .nonce(managementEntity.getRequestNonce())
                .version(applicationProperties.getRequestObjectVersion())
                .presentationDefinition(toPresentationDefinitionDto(presentation))
                .clientId(clientId)
                .clientMetadata(openIdClientMetadataConfiguration.getOpenIdClientMetadata())
                .clientIdScheme(applicationProperties.getClientIdScheme())
                .responseType("vp_token")
                .responseMode("direct_post")
                .responseUri(String.format("%s/oid4vp/api/request-object/%s/response-data",
                        externalUrl,
                        managementEntityId))
                .build();

        // if signing is not desired return request object
        if (Boolean.FALSE.equals(managementEntity.getJwtSecuredAuthorizationRequest())) {
            return requestObject;
        }
        SignerProvider signerProvider;
        try {
            if (override.keyId() != null) {
                signerProvider = signatureService.createSignerProvider(override.keyId(), override.keyPin());
            } else {
                signerProvider = signatureService.createDefaultSignerProvider();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize signature provider. This is probably because the key could not be loaded.", e);
        }
        JWSSigner signer = signerProvider.getSigner();

        if (!signerProvider.canProvideSigner()) {
            log.error("Upstream system error. Upstream system requested presentation to be signed despite the verifier not being configured for it");
            throw new IllegalStateException("Presentation was configured to be signed, but no signing key was configured.");
        }

        var jwsHeader = new JWSHeader
                .Builder(USED_JWS_ALGORITHM)
                .keyID(verificationMethod)
                .type(new JOSEObjectType("oauth-authz-req+jwt")) //as specified in https://www.rfc-editor.org/rfc/rfc9101.html#section-10.8
                .build();
        var signedJwt = new SignedJWT(jwsHeader, createJWTClaimsSet(clientId, requestObject));

        try {

            signedJwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Error signing JWT", e);
        }

        return signedJwt.serialize();
    }

    private JWTClaimsSet createJWTClaimsSet(String issuer, RequestObjectDto requestObject) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        builder.issuer(issuer);

        // get all properties of the request object
        Map<String, Object> requestObjectProperties = JsonUtil.getJsonObject(objectMapper.convertValue(requestObject, Map.class));
        requestObjectProperties.keySet().stream()
                // filter out null values
                .filter(key -> requestObjectProperties.get(key) != null)
                // add all non-null values to the JWT
                .forEach(key -> builder.claim(key, requestObjectProperties.get(key)));

        return builder.build();
    }

}