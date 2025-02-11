/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures;

import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.oid4vp.domain.management.PresentationDefinition.*;

@UtilityClass
public class PresentationDefinitionFixtures {

    public static PresentationDefinition sdwjtPresentationDefinition(UUID requestId) {
        var formats = Map.of("vc+sd-jwt", FormatAlgorithm.builder()
                .keyBindingAlg(List.of("ES256"))
                .alg(List.of("ES256")).build());

        return presentationDefinitionWithFields(
                requestId,
                List.of(
                        Field.builder().path(List.of("$.vct")).filter(Filter.builder().type("string").constDescriptor(SDJWTCredentialMock.DEFAULT_VCT).build()).build(),
                        Field.builder().path(List.of("$.last_name")).build(),
                        Field.builder().path(List.of("$.birthdate")).build()
                ),
                null,
                formats
        );
    }

    public static PresentationDefinition sdwjtPresentationDefinition(UUID requestId, List<String> requiredFields) {
        var formats = Map.of("vc+sd-jwt", FormatAlgorithm.builder()
                .keyBindingAlg(List.of("ES256"))
                .alg(List.of("ES256")).build());

        return presentationDefinition(requestId, requiredFields, null, formats);
    }

    public static PresentationDefinition presentationDefinition(UUID requestId,
                                                                List<String> fields,
                                                                Map<String, FormatAlgorithm> descriptorFormats,
                                                                Map<String, FormatAlgorithm> presentationFormats) {
        return presentationDefinitionWithFields(
                requestId,
                fields.stream().map(field -> Field.builder().path(List.of(field)).build()).toList(),
                descriptorFormats,
                presentationFormats
        );
    }

    public static PresentationDefinition presentationDefinitionWithDescriptorFormat(UUID requestId,
                                                                                    List<String> requiredFields,
                                                                                    Map<String, FormatAlgorithm> descriptorFormats) {

        return presentationDefinition(requestId, requiredFields, descriptorFormats, null);
    }

    public static PresentationDefinition presentationDefinitionWithFields(UUID requestId,
                                                                          List<Field> fields,
                                                                          Map<String, FormatAlgorithm> descriptorFormats,
                                                                          Map<String, FormatAlgorithm> presentationFormats) {
        Constraint constraint = Constraint.builder()
                .fields(fields)
                .build();

        InputDescriptor inputDescriptor = InputDescriptor.builder()
                .id("test_descriptor_id")
                .name("Test Descriptor Name")
                .constraints(constraint)
                .format(presentationFormats)
                .build();

        return PresentationDefinition.builder()
                .id(requestId.toString())
                .inputDescriptors(List.of(inputDescriptor))
                .format(descriptorFormats)
                .build();

    }
}
