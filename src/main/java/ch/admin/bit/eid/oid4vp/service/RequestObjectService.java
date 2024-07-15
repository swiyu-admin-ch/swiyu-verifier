package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.RequestObject;
import ch.admin.bit.eid.oid4vp.model.dto.VerifierMetadata;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@AllArgsConstructor
public class RequestObjectService {
    private final ApplicationConfiguration applicationConfiguration;
    private final VerificationManagementRepository managementRepository;

    public RequestObject assembleRequestObject(UUID presentationDefinitionId) {
        var managementEntity = managementRepository.findById(presentationDefinitionId.toString()).orElseThrow(
                () -> VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND));

        return RequestObject.builder()
                .nonce(managementEntity.getRequestNonce())
                .inputDescriptors(managementEntity.getRequestedPresentation().getInputDescriptors())
                .clientMetadata(VerifierMetadata.builder()
                        .clientName(applicationConfiguration.getClientName())
                        .logoUri(applicationConfiguration.getLogoUri())
                        .build())
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
