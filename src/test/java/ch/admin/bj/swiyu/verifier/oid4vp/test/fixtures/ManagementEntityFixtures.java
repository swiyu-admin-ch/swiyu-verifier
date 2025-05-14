/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures;

import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.ManagementEntity;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.VerificationStatus;
import lombok.experimental.UtilityClass;

import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;

@UtilityClass
public class ManagementEntityFixtures {

    public static ManagementEntity managementEntity(UUID requestId, PresentationDefinition definition) {
        return managementEntity(requestId, definition, DEFAULT_ISSUER_ID);
    }

    public static ManagementEntity managementEntity(UUID requestId, PresentationDefinition definition, String acceptedIssuerDids) {
        return ManagementEntity.builder()
                .id(requestId)
                .requestedPresentation(definition)
                .state(VerificationStatus.PENDING)
                .requestNonce("HelloNonce")
                .acceptedIssuerDids(acceptedIssuerDids)
                .build();
    }
}
