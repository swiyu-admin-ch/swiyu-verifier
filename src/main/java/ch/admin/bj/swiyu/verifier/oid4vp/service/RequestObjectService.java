/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.service;

import java.util.Map;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationErrorResponseCode.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND;
import static ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationErrorResponseCode.VERIFICATION_PROCESS_CLOSED;
import static ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationException.submissionError;
import static ch.admin.bj.swiyu.verifier.oid4vp.service.RequestObjectMapper.toPresentationDefinitionDto;

import ch.admin.bj.swiyu.verifier.oid4vp.api.requestobject.RequestObjectDto;
import ch.admin.bj.swiyu.verifier.oid4vp.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.common.config.OpenIdClientMetadataConfiguration;
import ch.admin.bj.swiyu.verifier.oid4vp.common.config.SignerProvider;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.ManagementEntityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@AllArgsConstructor
public class RequestObjectService {
    private final ApplicationProperties applicationProperties;
    private final OpenIdClientMetadataConfiguration openIdClientMetadataConfiguration;
    private final ManagementEntityRepository managementRepository;
    private final ObjectMapper objectMapper;
    private final SignerProvider signerProvider;

    @Transactional(readOnly = true)
    public Object assembleRequestObject(UUID managementEntityId) {

        log.info("Prepare request object for mgmt-id {}", managementEntityId);

        var managementEntity = managementRepository.findById(managementEntityId)
                .orElseThrow(() -> submissionError(AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND));

        if (!managementEntity.isVerificationPending()) {
            log.debug("ManagementEntity with id {} is requested after already processing it", managementEntityId);
            throw submissionError(VERIFICATION_PROCESS_CLOSED);
        }

        if (managementEntity.isExpired()) {
            log.debug("ManagementEntity with id {} is expired", managementEntityId);
            throw submissionError(AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND);
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
                .responseUri(String.format("%s/api/v1/request-object/%s/response-data",
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

        var jwsHeader = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(applicationProperties.getSigningKeyVerificationMethod()).build();
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
        Map<String, Object> requestObjectProperties = objectMapper.convertValue(requestObject, Map.class);
        requestObjectProperties.keySet().stream()
                // filter out null values
                .filter(key -> requestObjectProperties.get(key) != null)
                // add all non-null values to the JWT
                .forEach(key -> builder.claim(key, requestObjectProperties.get(key)));

        return builder.build();
    }

}
