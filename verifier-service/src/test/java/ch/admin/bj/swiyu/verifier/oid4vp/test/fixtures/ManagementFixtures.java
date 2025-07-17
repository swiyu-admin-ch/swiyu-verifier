/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures;

import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;

@UtilityClass
public class ManagementFixtures {

    public static Management managementEntity(UUID requestId, PresentationDefinition definition) {
        return managementEntity(requestId, definition, List.of(DEFAULT_ISSUER_ID));
    }

    public static Management managementEntity(UUID requestId, PresentationDefinition definition, List<String> acceptedIssuerDids) {
        return Management.builder()
                .id(requestId)
                .requestedPresentation(definition)
                .state(VerificationStatus.PENDING)
                .requestNonce("HelloNonce")
                .acceptedIssuerDids(acceptedIssuerDids)
                .build();
    }
}
