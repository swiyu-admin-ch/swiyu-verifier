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
import ch.admin.bit.eid.verifier_management.models.dto.CreateVerificationManagementDto;
import ch.admin.bit.eid.verifier_management.repositories.ManagementRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public Management getManagement(UUID id) {

        Management management = repository.findById(id).orElseThrow(() -> new VerificationNotFoundException(id));

        // TODO check expiry -> not possible at the moment only not found

        if (management.isExpired()) {
            repository.deleteById(id);

            log.info(createLoggingMessage("Verification result delivered and entry removed",
                    LogEntryStatus.SUCCESS,
                    LogEntryOperation.VERIFICATION,
                    LogEntryStep.VERIFICATION_RESPONSE,
                    management.getId()));
        }

        return management;
    }

    @Transactional
    public Management createVerificationManagement(CreateVerificationManagementDto requestDto) {

        if (requestDto == null) {
            throw new IllegalArgumentException("CreateVerificationManagement is null");
        }

        if (requestDto.getPresentationDefinition() == null) {
            throw new IllegalArgumentException("PresentationDefinition is null");
        }

        PresentationDefinition presentationDefinition = PresentationDefinitionMapper.map(requestDto.getPresentationDefinition());

        Management management = repository.save(Management.builder()
                .id(UUID.randomUUID())
                .state(VerificationStatusEnum.PENDING)
                .requestNonce(createNonce())
                .expirationInSeconds(applicationProperties.getVerificationTTL())
                .expiresAt(System.currentTimeMillis() + applicationProperties.getVerificationTTL() * 1000)
                .requestedPresentation(presentationDefinition)
                // if not set, default for jar is true (signed jwt)
                .jwtSecuredAuthorizationRequest(requestDto.getJwtSecuredAuthorizationRequest() != null ? requestDto.getJwtSecuredAuthorizationRequest() : true)
                .build());

        log.info(createLoggingMessage("Requesting verification",
                LogEntryStatus.SUCCESS,
                LogEntryOperation.VERIFICATION,
                LogEntryStep.VERIFICATION_REQUEST,
                management.getId()));

        return management;
    }

    @Transactional
    public void removeExpiredManagements() {
        repository.deleteByExpiresAtIsBefore(System.currentTimeMillis());
    }

    private String createNonce() {
        final SecureRandom random = new SecureRandom();
        final Base64.Encoder base64encoder = Base64.getEncoder().withoutPadding();

        byte[] randomBytes = new byte[24];
        random.nextBytes(randomBytes);

        return base64encoder.encodeToString(randomBytes);
    }
}
