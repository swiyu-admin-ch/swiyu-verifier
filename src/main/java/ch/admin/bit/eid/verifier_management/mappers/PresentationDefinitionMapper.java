package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.PresentationDefinition;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionDto;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class PresentationDefinitionMapper {

    public static PresentationDefinition map(PresentationDefinitionDto dto) {

        if (dto == null) {
            throw new IllegalArgumentException("PresentationDefinitionDto must not be null");
        }

        return PresentationDefinition.builder()
                .id(UUID.randomUUID().toString())
                .inputDescriptors(dto.getInputDescriptors())
                .format(dto.getFormat())
                .purpose(dto.getPurpose())
                .build();
    }

    public static PresentationDefinitionDto toDto(PresentationDefinition presentation) {

        if (presentation == null) {
            throw new IllegalArgumentException("Presentation must not be null");
        }

        return PresentationDefinitionDto.builder()
                .id(presentation.getId())
                .name(presentation.getName())
                .purpose(presentation.getPurpose())
                .format(presentation.getFormat())
                .inputDescriptors(presentation.getInputDescriptors())
                .build();
    }
}
