package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.PresentationDefinition;
import ch.admin.bit.eid.verifier_management.models.dto.*;
import ch.admin.bit.eid.verifier_management.models.entities.*;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.UUID;

@UtilityClass
public class PresentationDefinitionMapper {

    // TODO check validation
    public static PresentationDefinition buildPresentationDefinition(PresentationDefinitionDto requestDto) {

        if (requestDto == null) {
            throw new IllegalArgumentException("PresentationDefinitionDto must not be null");
        }

        return PresentationDefinition.builder().build();
    }
}
