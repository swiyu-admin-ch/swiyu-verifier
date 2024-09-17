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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class RequestObjectService {
    private final ApplicationProperties applicationProperties;
    private final VerificationManagementRepository managementRepository;

    @Transactional(readOnly = true)
    public RequestObject assembleRequestObject(UUID presentationDefinitionId) {

        log.info("Prepare request object for mgmt-id {}", presentationDefinitionId);

        ManagementEntity managementEntity = managementRepository.findById(presentationDefinitionId).orElseThrow(
                () -> VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND, null));

        PresentationDefinitionDto presentation = PresentationDefinitionMapper.toDto(managementEntity.getRequestedPresentation());

        VerifierMetadata metadata = VerifierMetadata.builder()
                .clientName(applicationProperties.getClientName())
                .logoUri(applicationProperties.getLogoUri())
                .build();

        return RequestObject.builder()
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
    }

}
