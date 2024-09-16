package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfig;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationDefinitionDto;
import ch.admin.bit.eid.oid4vp.model.dto.RequestObject;
import ch.admin.bit.eid.oid4vp.model.dto.VerifierMetadata;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.mapper.PresentationDefinitionMapper;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class RequestObjectService {
    private final ApplicationConfig applicationConfiguration;
    private final VerificationManagementRepository managementRepository;

    public RequestObject assembleRequestObject(UUID presentationDefinitionId) {

        log.info("Prepare request object for mgmt-id {}", presentationDefinitionId);

        ManagementEntity managementEntity = managementRepository.findById(presentationDefinitionId).orElseThrow(
                () -> VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND, null));

        PresentationDefinitionDto presentation = PresentationDefinitionMapper.toDto(managementEntity.getRequestedPresentation());

        VerifierMetadata metadata = VerifierMetadata.builder()
                .clientName(applicationConfiguration.getClientName())
                .logoUri(applicationConfiguration.getLogoUri())
                .build();

        return RequestObject.builder()
                .nonce(managementEntity.getRequestNonce())
                .presentationDefinition(presentation)
                .clientMetadata(metadata)
                .clientId(applicationConfiguration.getClientId())
                .clientIdScheme(applicationConfiguration.getClientIdScheme())
                .responseType("vp_token")
                .responseMode("direct_post")
                .responseUri(String.format("%s/request-object/%s/response-data",
                        applicationConfiguration.getExternalUrl(),
                        presentationDefinitionId))
                .build();
    }

}
