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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final SignerProvider signerProvider;

    @Transactional(readOnly = true)
    public Object assembleRequestObject(UUID managementEntityId) {

        log.info("Prepare request object for mgmt-id {}", managementEntityId);

        var managementEntity = managementRepository.findById(managementEntityId)
                .orElseThrow();

        if (!managementEntity.isVerificationPending()) {
            log.debug("Management with id {} is requested after already processing it", managementEntityId);
            throw new ProcessClosedException();
        }

        if (managementEntity.isExpired()) {
            log.debug("Management with id {} is expired", managementEntityId);
            throw new NoSuchElementException("Verification Request with id " + managementEntityId + " is expired");
        }

        var presentation = managementEntity.getRequestedPresentation();

        var requestObject = RequestObjectDto.builder()
                .nonce(managementEntity.getRequestNonce())
                .version(applicationProperties.getRequestObjectVersion())
                .presentationDefinition(toPresentationDefinitionDto(presentation))
                .clientId(applicationProperties.getClientId())
                .clientMetadata(openIdClientMetadataConfiguration.getOpenIdClientMetadata())
                .clientIdScheme(applicationProperties.getClientIdScheme())
                .responseType("vp_token")
                .responseMode("direct_post")
                .responseUri(String.format("%s/api/v1/public/request-object/%s/response-data",
                        applicationProperties.getExternalUrl(),
                        managementEntityId))
                .build();

        // if signing is not desired return request object
        if (!managementEntity.getJwtSecuredAuthorizationRequest()) {
            return requestObject;
        }
        if (!signerProvider.canProvideSigner()) {
            log.error("Upstream system error. Upstream system requested presentation to be signed despite the verifier not being configured for it");
            throw new IllegalStateException("Presentation was configured to be signed, but no signing key was configured.");
        }

        var jwsHeader = new JWSHeader
                .Builder(USED_JWS_ALGORITHM)
                .keyID(applicationProperties.getSigningKeyVerificationMethod())
                .type(new JOSEObjectType("oauth-authz-req+jwt")) //as specified in https://www.rfc-editor.org/rfc/rfc9101.html#section-10.8
                .build();
        var signedJwt = new SignedJWT(jwsHeader, createJWTClaimsSet(requestObject));

        try {
            signedJwt.sign(signerProvider.getSigner());
        } catch (JOSEException e) {
            throw new IllegalStateException("Error signing JWT", e);
        }

        return signedJwt.serialize();
    }

    private JWTClaimsSet createJWTClaimsSet(RequestObjectDto requestObject) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        builder.issuer(applicationProperties.getClientId());

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