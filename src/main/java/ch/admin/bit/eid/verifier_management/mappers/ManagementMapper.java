package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.Management;
import ch.admin.bit.eid.verifier_management.models.dto.ManagementResponseDto;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionDto;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ManagementMapper {

    public static ManagementResponseDto toDto(final Management management, final String oid4vpUrl) {
        if (management == null) {
            throw new IllegalArgumentException("Management must not be null");
        }

        String verificationUrl = String.format("%s/request-object/%s", oid4vpUrl, management.getWalletResponse().getId());

        PresentationDefinitionDto presentationDefinitionDto = management.getRequestedPresentation() != null
                ? PresentationDefinitionMapper.toDto(management.getRequestedPresentation())
                : null;

        return ManagementResponseDto.builder()
                .id(management.getId())
                .requestNonce(management.getRequestNonce())
                .state(management.getState())
                .presentationDefinition(presentationDefinitionDto)
                .walletResponse(ResponseDataMapper.toDto(management.getWalletResponse()))
                .verificationUrl(verificationUrl)
                .build();
    }
}
