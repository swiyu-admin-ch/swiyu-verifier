/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.test.fixtures;

import ch.admin.bj.swiyu.verifier.api.definition.*;
import ch.admin.bj.swiyu.verifier.api.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.api.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.api.management.dcql.*;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static org.hibernate.internal.util.collections.CollectionHelper.isEmpty;

@UtilityClass
public class ApiFixtures {

    public static CreateVerificationManagementDto createVerificationManagementDto(List<String> acceptedIssuerDids) {
        return createVerificationManagementDto(acceptedIssuerDids, presentationDefinitionDto());
    }

    public static CreateVerificationManagementDto createVerificationManagementDto(List<String> acceptedIssuerDids, PresentationDefinitionDto presentationDefinitionDto) {
        return CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(acceptedIssuerDids)
                .jwtSecuredAuthorizationRequest(false)
                .responseMode(ResponseModeTypeDto.DIRECT_POST)
                .presentationDefinition(presentationDefinitionDto)
                .build();
    }

    public static CreateVerificationManagementDto createVerificationManagementWithDcqlQueryDto(PresentationDefinitionDto presentationDefinitionDto, DcqlQueryDto dcqlQueryDto, List<String> acceptedIssuerDids) {
        return CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(acceptedIssuerDids)
                .jwtSecuredAuthorizationRequest(false)
                .responseMode(ResponseModeTypeDto.DIRECT_POST)
                .presentationDefinition(presentationDefinitionDto)
                .dcqlQuery(dcqlQueryDto)
                .build();
    }

    public static CreateVerificationManagementDto createVerificationManagementWithDcqlQueryDto(DcqlQueryDto dcqlQueryDto, List<String> acceptedIssuerDids) {
        return CreateVerificationManagementDto.builder()
                .jwtSecuredAuthorizationRequest(false)
                .responseMode(ResponseModeTypeDto.DIRECT_POST)
                .presentationDefinition(presentationDefinitionDto())
                .acceptedIssuerDids(acceptedIssuerDids)
                .dcqlQuery(dcqlQueryDto)
                .build();
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

        PresentationDefinitionDto presentationDefinitionDto = PresentationDefinitionDto.builder()
                .id(UUID.randomUUID().toString())
                .inputDescriptors(new ArrayList<>(List.of(inputDescriptorMinimal)))
                .build();
        return new CreateVerificationManagementDto(
                List.of("did:example:123"), null,
                isJWTSecured, ResponseModeTypeDto.DIRECT_POST,
                presentationDefinitionDto,
                null,
                null
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

    @NotNull
    public static DcqlQueryDto getDcqlQueryDto() {
        var claims = List.of(
                new DcqlClaimDto(null, List.of("given_name"), null),
                new DcqlClaimDto(null, List.of("family_name"),null)
        );
        return createDcqlQueryDto(claims);
    }

    public static DcqlQueryDto createDcqlQueryDto(List<DcqlClaimDto> claims) {
        // Build a minimal DCQL query DTO
        var meta = new DcqlCredentialMetaDto(
                null,
                List.of("https://credentials.example.com/identity_credential"),
                null
        );
        var credential = new DcqlCredentialDto(
                "identity_credential_dcql",
                "dc+sd-jwt",
                null,
                meta,
                claims,
                null,
                true,
                null
        );

        return new DcqlQueryDto(
                List.of(credential),
                List.of()
        );
    }
}