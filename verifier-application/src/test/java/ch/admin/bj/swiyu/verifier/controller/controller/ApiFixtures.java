/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.controller.controller;

import ch.admin.bj.swiyu.verifier.api.definition.*;
import ch.admin.bj.swiyu.verifier.api.management.CreateVerificationManagementDto;
import lombok.experimental.UtilityClass;

import java.util.*;

import static org.hibernate.internal.util.collections.CollectionHelper.isEmpty;

@UtilityClass
public class ApiFixtures {

    public static CreateVerificationManagementDto createVerificationManagementDto() {
        return createVerificationManagementDto(null, presentationDefinitionDto());
    }

    public static CreateVerificationManagementDto createVerificationManagementDto(List<String> acceptedIssuerDids) {
        return createVerificationManagementDto(acceptedIssuerDids, presentationDefinitionDto());
    }

    public static CreateVerificationManagementDto createVerificationManagementDto(List<String> acceptedIssuerDids, PresentationDefinitionDto presentationDefinitionDto) {
        return new CreateVerificationManagementDto(acceptedIssuerDids, false, presentationDefinitionDto);
    }

    public static CreateVerificationManagementDto createVerificationManagementDto_Minimal(boolean isJWTSecured) {
        var inputDescriptorMinimal = new InputDescriptorDto(
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                new ConstraintDto(
                        UUID.randomUUID().toString(),
                        null,
                        null,
                        null,
                        new ArrayList<>(List.of(new FieldDto(List.of("string"), null, null, null, null)))
                )
        );
        return new CreateVerificationManagementDto(
                List.of("did:example:123"),
                isJWTSecured, new PresentationDefinitionDto(
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                List.of(inputDescriptorMinimal))
        );
    }

    public static PresentationDefinitionDto presentationDefinitionDto() {
        return new PresentationDefinitionDto(
                UUID.randomUUID().toString(),
                "presentation_definition_name",
                "presentation_definition_purpose",
                formatAlgorithmDtoMap(),
                new ArrayList<>(List.of(inputDescriptorDto())));
    }

    public static InputDescriptorDto inputDescriptorDto() {
        return inputDescriptorDto(UUID.randomUUID().toString());
    }

    public static InputDescriptorDto inputDescriptorDto_WithoutConstraints() {
        return new InputDescriptorDto(
                UUID.randomUUID().toString(),
                "input_descriptor_name",
                "input_descriptor_purpose",
                formatAlgorithmDtoMap(),
                null);
    }

    public static InputDescriptorDto inputDescriptorDto_Invalid() {
        return new InputDescriptorDto(
                UUID.randomUUID().toString(),
                "input_descriptor_name",
                "input_descriptor_purpose",
                Map.of("WeakCrypt", new FormatAlgorithmDto(null, null)),
                constraintDto());
    }

    public static InputDescriptorDto inputDescriptorDto(String id) {
        return new InputDescriptorDto(
                id,
                "input_descriptor_name",
                "input_descriptor_purpose",
                formatAlgorithmDtoMap(),
                constraintDto());
    }

    public static Map<String, FormatAlgorithmDto> formatAlgorithmDtoMap() {
        return new HashMap<>(Map.of("vc+sd-jwt", formatAlgorithmDto()));
    }

    private static FormatAlgorithmDto formatAlgorithmDto() {
        return new FormatAlgorithmDto(List.of("ES256"), List.of("ES256"));
    }

    private static ConstraintDto constraintDto() {
        return constraintDto(null);
    }

    private static ConstraintDto constraintDto(Map<String, FormatAlgorithmDto> format) {
        return new ConstraintDto(
                UUID.randomUUID().toString(),
                null,
                null,
                format,
                new ArrayList<>(List.of(fieldDto(List.of())))
        );
    }

    public static FieldDto fieldDto(List<String> path) {
        return new FieldDto(
                isEmpty(path) ? List.of("$.test", "$.test2") : path,
                UUID.randomUUID().toString(),
                "field_name",
                "field_purpose",
                null);
    }
}
