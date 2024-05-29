package ch.admin.bit.eid.verifier_management.services;

import ch.admin.bit.eid.verifier_management.config.OpenId4VPConfig;
import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import ch.admin.bit.eid.verifier_management.exceptions.ResourceNotFoundException;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionRequestDto;
import ch.admin.bit.eid.verifier_management.models.entities.PresentationDefinition;
import ch.admin.bit.eid.verifier_management.models.entities.VerificationManagement;
import ch.admin.bit.eid.verifier_management.models.entities.VerificationRequestObject;
import ch.admin.bit.eid.verifier_management.repositories.VerificationManagementRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@AllArgsConstructor
public class VerificationManagementService {

    private final OpenId4VPConfig openId4VPConfig;

    private final VerificationManagementRepository repository;

    private final VerificationRequestService requestService;

    private final PresentationDefinitionService presentationDefinitionService;

    public VerificationManagement getVerification(UUID verificationId) {

        // TODO consider raise err.VerificationNotFoundError()
        return repository.findById(verificationId.toString()).orElseThrow(() ->
                new ResourceNotFoundException(verificationId));
    }

    public VerificationManagement createVerificationManagement(PresentationDefinitionRequestDto requestDto) {

        if (requestDto == null) {
            throw new IllegalArgumentException("PresentationDefinition is null");
        }

        PresentationDefinition presentation = presentationDefinitionService.createPresentationDefinition(requestDto);

        VerificationRequestObject authRequest = requestService.createAuthorizationRequest(presentation);

        UUID authRequestId = authRequest.getId();

        String authorizationRequestObjectUri = String.format(openId4VPConfig.getRequestObjectPattern(), authRequestId);

        VerificationManagement verificationManagement = VerificationManagement.builder()
                .id(UUID.randomUUID())
                .status(VerificationStatusEnum.PENDING)
                .authorizationRequestId(authRequestId)
                .authorizationRequestObjectUri(authorizationRequestObjectUri)
                .build();

        return repository.save(verificationManagement);
    }
}
