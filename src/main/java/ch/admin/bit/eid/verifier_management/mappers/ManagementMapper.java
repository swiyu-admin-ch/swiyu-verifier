package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.Management;
import ch.admin.bit.eid.verifier_management.models.dto.CreateManagementResponseDto;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ManagementMapper {

    public static CreateManagementResponseDto managementToManagementDto(Management management) {
        if (management == null) {
            throw new IllegalArgumentException("Management must not be null");
        }

        return CreateManagementResponseDto
                .builder()
                .id(management.getId())
                .requestNonce(management.getRequestNonce())
                .state(management.getState())
                .build();

    }
}
