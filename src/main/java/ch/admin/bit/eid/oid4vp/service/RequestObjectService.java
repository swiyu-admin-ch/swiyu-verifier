package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.config.ApplicationProperties;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationDefinitionDto;
import ch.admin.bit.eid.oid4vp.model.dto.RequestObject;
import ch.admin.bit.eid.oid4vp.model.dto.VerifierMetadata;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.mapper.PresentationDefinitionMapper;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class RequestObjectService {
    private final ApplicationProperties applicationProperties;
    private final VerificationManagementRepository managementRepository;
    private final ECDSASigner signer;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public String assembleRequestObjectJwt(UUID presentationDefinitionId) {

        log.info("Prepare request object for mgmt-id {}", presentationDefinitionId);

        ManagementEntity managementEntity = managementRepository.findById(presentationDefinitionId).orElseThrow(
                () -> VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND, null));

        PresentationDefinitionDto presentation = PresentationDefinitionMapper.toDto(managementEntity.getRequestedPresentation());

        VerifierMetadata metadata = VerifierMetadata.builder()
                .clientName(applicationProperties.getClientName())
                .logoUri(applicationProperties.getLogoUri())
                .build();

        RequestObject requestObject = RequestObject.builder()
                .nonce(managementEntity.getRequestNonce())
                .presentationDefinition(presentation)
                .clientMetadata(metadata)
                .clientId(applicationProperties.getClientId())
                .clientIdScheme(applicationProperties.getClientIdScheme())
                .responseType("vp_token")
                .responseMode("direct_post")
                .responseUri(String.format("%s/request-object/%s/response-data",
                        applicationProperties.getExternalUrl(),
                        presentationDefinitionId))
                .build();

        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(applicationProperties.getSigningKey()).build(),
                createJWTClaimsSet(requestObject)
        );

        try {
            signedJwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Error signing JWT", e);
        }

        return signedJwt.serialize();
    }

    private JWTClaimsSet createJWTClaimsSet(RequestObject requestObject) {
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
