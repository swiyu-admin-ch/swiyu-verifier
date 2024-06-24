package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.PresentationDefinition;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionDto;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PresentationDefinitionMapper {

    public static PresentationDefinitionDto toDto(PresentationDefinition presentation) {

        if (presentation == null) {
            throw new IllegalArgumentException("Presentation must not be null");
        }

        return PresentationDefinitionDto.builder()
                .id(presentation.getId())
                .name(presentation.getName())
                .purpose(presentation.getPurpose())
                .inputDescriptors(presentation.getInputDescriptors())
                .format(presentation.getFormat())
                .build();
    }
}
