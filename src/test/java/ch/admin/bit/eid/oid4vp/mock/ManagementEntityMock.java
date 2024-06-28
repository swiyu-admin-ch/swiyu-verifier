package ch.admin.bit.eid.oid4vp.mock;

import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class ManagementEntityMock {

    public static ManagementEntity getManagementEntityMock(UUID requestId, PresentationDefinition definition) {

        return ManagementEntity.builder()
                .id(requestId)
                .requestedPresentation(definition)
                .state(VerificationStatusEnum.PENDING)
                .requestNonce("HelloNonce")
                .build();
    }
}
