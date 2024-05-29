package ch.admin.bit.eid.verifier_management.services;

import ch.admin.bit.eid.verifier_management.config.ApplicationConfig;
import ch.admin.bit.eid.verifier_management.mappers.PresentationDefinitionMapper;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionRequestDto;
import ch.admin.bit.eid.verifier_management.models.entities.PresentationDefinition;
import ch.admin.bit.eid.verifier_management.repositories.PresentationDefinitionRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class PresentationDefinitionService {

    private final ApplicationConfig appConfig;

    private final PresentationDefinitionRepository repository;

    public PresentationDefinition createPresentationDefinition(PresentationDefinitionRequestDto requestDto) {

        if (requestDto == null) {
            throw new IllegalArgumentException("PresentationDefinitionRequest is null");
        }

        Integer expiresAt = appConfig.getVerificationTTL();

        PresentationDefinition presentationDefinition = PresentationDefinitionMapper.buildPresentationDefinition(requestDto,
                expiresAt);

        return repository.save(presentationDefinition);
    }
}
