package ch.admin.bj.swiyu.verifier.service.management.fixtures;

import ch.admin.bj.swiyu.verifier.domain.management.Management;

import java.util.List;

import static org.hibernate.internal.util.collections.CollectionHelper.isEmpty;

public class ManagementFixtures {
    public static Management management() {
        return management(900);
    }

    public static Management management(int expirationInSeconds) {
        return Management.builder()
                .expirationInSeconds(expirationInSeconds)
                .jwtSecuredAuthorizationRequest(true)
                .acceptedIssuerDids(List.of("did:example:123"))
                .build()
                .resetExpiresAt();
    }

}
