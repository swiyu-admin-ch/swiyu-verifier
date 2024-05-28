package ch.admin.bit.eid.verifier_management.services;

import ch.admin.bit.eid.verifier_management.config.ApplicationConfig;
import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import ch.admin.bit.eid.verifier_management.exceptions.ResourceNotFoundException;
import ch.admin.bit.eid.verifier_management.mappers.PresentationDefinitionMapper;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionRequestDto;
import ch.admin.bit.eid.verifier_management.models.entities.PresentationDefinition;
import ch.admin.bit.eid.verifier_management.models.entities.VerificationManagement;
import ch.admin.bit.eid.verifier_management.models.entities.VerificationRequestObject;
import ch.admin.bit.eid.verifier_management.repositories.PresentationDefinitionRepository;
import ch.admin.bit.eid.verifier_management.repositories.VerificationManagementRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@AllArgsConstructor
public class PresentationService {

    private final ApplicationConfig appConfig;

    private final PresentationDefinitionRepository repository;

    private final VerificationManagementRepository verificationManagementRepository;

    public VerificationManagement getVerification(UUID verificationId) {

        VerificationManagement verificationManagement = verificationManagementRepository.findById(verificationId.toString()).orElseThrow(() ->
                new ResourceNotFoundException(verificationId));

        return verificationManagement;
    }

    public VerificationManagement createVerificationManagement(
            PresentationDefinitionRequestDto requestDto) {

        PresentationDefinition presentationDefinition = this.createPresentationDefinition(requestDto);

        String authorizationRequestId = this.createAuthorizationRequest(presentationDefinition);

        // TODO check if needed or uri can be used
        String authorizationRequestObjectUri = String.format("%s/request-object/%s", appConfig.getVerifierUrl(),
                authorizationRequestId);

        // TODO check if needed
        VerificationManagement verificationManagement = VerificationManagement.builder()
                .id(UUID.randomUUID())
                .status(VerificationStatusEnum.PENDING)
                .authorizationRequestId(authorizationRequestId)
                .authorizationRequestObjectUri(authorizationRequestObjectUri)
                .build();

        return verificationManagementRepository.save(verificationManagement);
    }

    private PresentationDefinition createPresentationDefinition(PresentationDefinitionRequestDto requestDto) {
        Integer expiresAt = appConfig.getVerificationTTL();
        PresentationDefinition presentationDefinition = PresentationDefinitionMapper.buildPresentationDefinition(requestDto,
                expiresAt);

        return repository.save(presentationDefinition);

    }

    private String createAuthorizationRequest(PresentationDefinition presentationDefinition) {
        UUID uuid = UUID.randomUUID();
        // todo check if /response data really not needed
        String uri = String.format("%s/request-object/%s/response-data", appConfig.getVerifierUrl(), uuid);

        VerificationRequestObject verificationRequest = VerificationRequestObject.builder()
                .id(uuid)
                // TODO check -> was nonce=uuid.uuid4().hex
                .nonce(UUID.randomUUID().toString())
                // TODO check if enum
                .responseMode("direct_post")
                .responseUri(uri)
                .clientMetadata(presentationDefinition.getClientMetadata())
                .presentationDefinition(presentationDefinition)
                .expiresAt(appConfig.getVerificationTTL())
                .build();

        return uuid.toString();
    }
}
