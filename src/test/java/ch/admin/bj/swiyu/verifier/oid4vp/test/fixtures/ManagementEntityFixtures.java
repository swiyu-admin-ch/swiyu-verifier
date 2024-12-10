package ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures;

import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.ManagementEntity;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.VerificationStatus;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class ManagementEntityFixtures {

    public static ManagementEntity managementEntity(UUID requestId, PresentationDefinition definition) {
        return ManagementEntity.builder()
                .id(requestId)
                .requestedPresentation(definition)
                .state(VerificationStatus.PENDING)
                .requestNonce("HelloNonce")
                .build();
    }
}
