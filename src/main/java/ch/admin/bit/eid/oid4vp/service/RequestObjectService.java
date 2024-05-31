package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.model.dto.RequestObject;
import ch.admin.bit.eid.oid4vp.model.dto.VerifierMetadata;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Service
@AllArgsConstructor
public class RequestObjectService {
    private final ApplicationConfiguration applicationConfiguration;
    private final VerificationManagementRepository managementRepository;

    public RequestObject assembleRequestObject(UUID presentationDefinitionId) throws IOException {
        var managementEntity = managementRepository.findById(presentationDefinitionId.toString()).orElseThrow();
        return RequestObject.builder()
                .nonce(managementEntity.getRequestNonce())
                .inputDescriptors(managementEntity.getRequestedPresentation().getInputDescriptors())
                .clientMetadata(VerifierMetadata.builder()
                        .client_name(applicationConfiguration.getClientName())
                        .logo_uri(applicationConfiguration.getLogoUri())
                        .build())
                .clientId(applicationConfiguration.getClientId())
                .clientIdScheme(applicationConfiguration.getClientIdScheme())
                .build();
    }

}
