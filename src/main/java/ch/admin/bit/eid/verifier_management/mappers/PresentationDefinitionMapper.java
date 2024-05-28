package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.dto.*;
import ch.admin.bit.eid.verifier_management.models.entities.*;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.UUID;

@UtilityClass
public class PresentationDefinitionMapper {

    public static PresentationDefinition buildPresentationDefinition(PresentationDefinitionRequestDto requestDto, Integer expiresAt) {
        return PresentationDefinition.builder()
                .id(UUID.randomUUID())
                .clientMetadata(mapClientMetadata(requestDto.getClientMetadata()))
                .inputDescriptors(mapInputDescriptorList(requestDto.getInputDescriptors()))
                .expiresAt(expiresAt)
                .build();
    }

    private static ClientMetadata mapClientMetadata(ClientMetadataDto requestDto) {

        // TODO check if not null
        return ClientMetadata.builder()
                .clientName(requestDto.getClient_name())
                .logoUri(requestDto.getLogo_uri())
                .build();
    }

    private static List<InputDescriptor> mapInputDescriptorList(List<InputDescriptorDto> requestDtoList) {
        return requestDtoList.stream().map(PresentationDefinitionMapper::mapInputDescriptor).toList();
    }

    private static InputDescriptor mapInputDescriptor(InputDescriptorDto requestDto) {
        return InputDescriptor.builder()
                .id(requestDto.getId())
                .format(requestDto.getFormat())
                .fields(mapFields(requestDto.getFields()))
                .build();
    }

    private static FieldsModel mapFields(FieldsDto requestDto) {
        return FieldsModel.builder()
                .fields(mapConstraintModelList(requestDto.getFields()))
                .build();
    }

    private static List<ConstraintModel> mapConstraintModelList(List<ConstraintDto> requestDtoList) {
        return requestDtoList.stream().map(PresentationDefinitionMapper::mapConstraintModel).toList();
    }

    private static ConstraintModel mapConstraintModel(ConstraintDto requestDto) {
        return ConstraintModel.builder()
                .path(requestDto.getPath())
                .filter(mapFilterModel(requestDto.getFilter()))
                .build();
    }

    private static FilterModel mapFilterModel(FilterDto requestDto) {
        return FilterModel.builder()
                .pattern(requestDto.getPattern())
                .type(requestDto.getType())
                .build();
    }
}
