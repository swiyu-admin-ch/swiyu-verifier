package ch.admin.bit.eid.verifier_management.services;

import ch.admin.bit.eid.verifier_management.config.ApplicationConfig;
import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import ch.admin.bit.eid.verifier_management.exceptions.VerificationNotFoundException;
import ch.admin.bit.eid.verifier_management.models.Management;
import ch.admin.bit.eid.verifier_management.models.PresentationDefinition;
import ch.admin.bit.eid.verifier_management.models.ResponseData;
import ch.admin.bit.eid.verifier_management.models.dto.CreateManagementRequestDto;
import ch.admin.bit.eid.verifier_management.repositories.ManagementRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static ch.admin.bit.eid.verifier_management.mappers.InputDescriptorMapper.InputDescriptorDTOsToInputDescriptors;
import static ch.admin.bit.eid.verifier_management.utils.MapperUtil.MapToJsonString;

@Service
@AllArgsConstructor
public class ManagementService {

    private final ManagementRepository repository;

    private final ApplicationConfig applicationConfig;

    public Management getManagement(UUID id) {
        Management mgmt = repository.findById(id).orElseThrow(() -> new VerificationNotFoundException(id));

        return mgmt;
    }

    public Management createVerificationManagement(CreateManagementRequestDto requestDto) {

        if (requestDto == null) {
            throw new IllegalArgumentException("PresentationDefinition is null");
        }

        PresentationDefinition presentationDefinition = PresentationDefinition.builder()
                .id(UUID.randomUUID())
                .inputDescriptors(InputDescriptorDTOsToInputDescriptors(requestDto.getInputDescriptors()))
                .submissionRequirements(MapToJsonString(requestDto.getCredentialSubjectData()))
                .build();

        ResponseData responseData = ResponseData.builder()
                .id(UUID.randomUUID())
                .credentialSubjectData(MapToJsonString(requestDto.getCredentialSubjectData()))
                .build();

        Management management = Management.builder()
                .id(UUID.randomUUID())
                .state(VerificationStatusEnum.PENDING)
                .requestNonce(createNonce())
                .expirationInSeconds(applicationConfig.getVerificationTTL())
                .requestedPresentation(presentationDefinition)
                .walletResponse(responseData)
                .build();

        Management savedManagement = repository.save(management);

        return savedManagement;
    }

    // TODO check -> was nonce=uuid.uuid4().hex
    private String createNonce() {
        final SecureRandom random = new SecureRandom();
        final Base64.Encoder base64encoder = Base64.getEncoder().withoutPadding();

        byte[] randomBytes = new byte[24];
        random.nextBytes(randomBytes);

        return base64encoder.encodeToString(randomBytes);
    }
}
