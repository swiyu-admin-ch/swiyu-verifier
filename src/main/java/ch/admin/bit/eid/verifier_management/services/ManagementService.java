package ch.admin.bit.eid.verifier_management.services;

import ch.admin.bit.eid.verifier_management.config.ApplicationProperties;
import ch.admin.bit.eid.verifier_management.enums.LogEntryOperation;
import ch.admin.bit.eid.verifier_management.enums.LogEntryStatus;
import ch.admin.bit.eid.verifier_management.enums.LogEntryStep;
import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import ch.admin.bit.eid.verifier_management.exceptions.VerificationNotFoundException;
import ch.admin.bit.eid.verifier_management.mappers.PresentationDefinitionMapper;
import ch.admin.bit.eid.verifier_management.models.Management;
import ch.admin.bit.eid.verifier_management.models.PresentationDefinition;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionDto;
import ch.admin.bit.eid.verifier_management.repositories.ManagementRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static ch.admin.bit.eid.verifier_management.utils.LoggingUtil.createLoggingMessage;

@Service
@AllArgsConstructor
@Slf4j
public class ManagementService {

    private final ManagementRepository repository;

    private final ApplicationProperties applicationProperties;

    public Management getManagement(UUID id) {

        Management management = repository.findById(id).orElseThrow(() -> new VerificationNotFoundException(id));

        // TODO check expiry -> not possible at the moment only not found

        if (management.getState() != VerificationStatusEnum.PENDING) {
            repository.deleteById(id);

            log.info(createLoggingMessage("Verification result delivered and entry removed",
                    LogEntryStatus.SUCCESS,
                    LogEntryOperation.VERIFICATION,
                    LogEntryStep.VERIFICATION_RESPONSE,
                    management.getId()));
        }

        return management;
    }

    public Management createVerificationManagement(PresentationDefinitionDto requestDto) {

        if (requestDto == null) {
            throw new IllegalArgumentException("PresentationDefinition is null");
        }

        PresentationDefinition presentationDefinition = PresentationDefinitionMapper.map(requestDto);

        Management management = repository.save(Management.builder()
                .id(UUID.randomUUID())
                .state(VerificationStatusEnum.PENDING)
                .requestNonce(createNonce())
                .expirationInSeconds(applicationProperties.getVerificationTTL())
                .requestedPresentation(presentationDefinition)
                .build());

        log.info(createLoggingMessage("Requesting verification",
                LogEntryStatus.SUCCESS,
                LogEntryOperation.VERIFICATION,
                LogEntryStep.VERIFICATION_REQUEST,
                management.getId()));

        return management;
    }

    private String createNonce() {
        final SecureRandom random = new SecureRandom();
        final Base64.Encoder base64encoder = Base64.getEncoder().withoutPadding();

        byte[] randomBytes = new byte[24];
        random.nextBytes(randomBytes);

        return base64encoder.encodeToString(randomBytes);
    }
}
