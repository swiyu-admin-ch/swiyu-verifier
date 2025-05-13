/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.test.fixtures;

import ch.admin.bj.swiyu.verifier.management.domain.management.Management;
import ch.admin.bj.swiyu.verifier.management.domain.management.PresentationDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hibernate.internal.util.collections.CollectionHelper.isEmpty;

public class ManagementFixtures {
    public static Management management() {
        return management(900);
    }

    public static Management management(int expirationInSeconds) {
        return new Management(UUID.randomUUID(), expirationInSeconds, presentationDefinition(), true, List.of("did:example:123"));
    }

    private static PresentationDefinition presentationDefinition() {
        return new PresentationDefinition(
                UUID.randomUUID().toString(),
                "presentation_definition_name",
                "presentation_definition_purpose",
                formatAlgorithmMap(),
                List.of(inputDescriptor())
        );
    }

    public static PresentationDefinition.InputDescriptor inputDescriptor() {
        return new PresentationDefinition.InputDescriptor(
                UUID.randomUUID().toString(),
                "input_descriptor_name",
                "input_descriptor_purpose",
                Map.of("vc+sd-jwt", formatAlgorithm()),
                constraint()
        );
    }

    private static PresentationDefinition.FormatAlgorithm formatAlgorithm() {
        return new PresentationDefinition.FormatAlgorithm(List.of("ES256"), List.of("ES256"));
    }

    private static PresentationDefinition.Constraint constraint() {
        return new PresentationDefinition.Constraint(
                UUID.randomUUID().toString(),
                "constraint_name",
                "constraint_purpose",
                formatAlgorithmMap(),
                List.of(field(List.of()))
        );
    }

    public static PresentationDefinition.Field field(List<String> path) {
        return new PresentationDefinition.Field(
                isEmpty(path) ? List.of("$.test", "$.test2") : path,
                UUID.randomUUID().toString(),
                "field_name",
                "field_purpose",
                null);
    }

    public static Map<String, PresentationDefinition.FormatAlgorithm> formatAlgorithmMap() {
        var formats = List.of("EC256");

        return new HashMap<>(Map.of("vc+sd-jwt", new PresentationDefinition.FormatAlgorithm(formats, formats)));
    }
}
