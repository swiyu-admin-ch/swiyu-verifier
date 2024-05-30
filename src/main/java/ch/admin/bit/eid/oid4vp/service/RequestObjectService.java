package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.model.dto.RequestObject;
import ch.admin.bit.eid.oid4vp.repository.PresentationDefinitionRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@AllArgsConstructor
public class RequestObjectService {
    private final ApplicationConfiguration applicationConfiguration;
    private final PresentationDefinitionRepository presentationDefinitionRepository;

    public RequestObject assembleRequestObject(UUID presentationDefinitionId) {
        var presentationDefinition = presentationDefinitionRepository.findById(presentationDefinitionId).orElseThrow();
        RequestObject.builder()
                .nonce(presentationDefinition.get)
    }

}
