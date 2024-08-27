package ch.admin.bit.eid.oid4vp.model.mapper;

import ch.admin.bit.eid.oid4vp.model.dto.PresentationDefinitionDto;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PresentationDefinitionMapper {

    public static PresentationDefinitionDto toDto(PresentationDefinition presentationDefinition) {

        if (presentationDefinition == null) {
            throw new IllegalArgumentException("PresentationDefinition must not be null");
        }

        return PresentationDefinitionDto.builder()
                .id(presentationDefinition.getId())
                .name(presentationDefinition.getName())
                .purpose(presentationDefinition.getPurpose())
                .format(presentationDefinition.getFormat())
                .inputDescriptors(presentationDefinition.getInputDescriptors())
                .build();
    }
}
