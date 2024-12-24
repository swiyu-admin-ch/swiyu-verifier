package ch.admin.bj.swiyu.verifier.management.test.fixtures;

import ch.admin.bj.swiyu.verifier.management.api.definition.*;
import ch.admin.bj.swiyu.verifier.management.api.management.CreateVerificationManagementDto;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hibernate.internal.util.collections.CollectionHelper.isEmpty;

@UtilityClass
public class ApiFixtures {

    public static CreateVerificationManagementDto createVerificationManagementDto() {
        return createVerificationManagementDto(presentationDefinitionDto());
    }

    public static CreateVerificationManagementDto createVerificationManagementDto(PresentationDefinitionDto presentationDefinition) {
        return CreateVerificationManagementDto.builder()
                .jwtSecuredAuthorizationRequest(false)
                .presentationDefinition(presentationDefinition)
                .build();
    }

    public static CreateVerificationManagementDto createVerificationManagementDto_Minimal(boolean isJWTSecured) {
        var pres = PresentationDefinitionDto.builder()
                .inputDescriptors(
                        List.of(InputDescriptorDto.builder()
                                .id(UUID.randomUUID().toString())
                                .constraints(ConstraintDto.builder()
                                        .fields(List.of(FieldDto.builder()
                                                .path(List.of("string"))
                                                .build()))
                                        .build())
                                .build()))
                .build();
        return CreateVerificationManagementDto.builder()
                .jwtSecuredAuthorizationRequest(isJWTSecured)
                .presentationDefinition(pres)
                .build();
    }

    public static PresentationDefinitionDto presentationDefinitionDto() {
        return PresentationDefinitionDto
                .builder()
                .id(UUID.randomUUID().toString())
                .name("presentation_definition_name")
                .purpose("presentation_definition_purpose")
                .inputDescriptors(List.of(inputDescriptorDto()))
                .format(formatAlgorithmDtoMap())
                .build();
    }

    public static InputDescriptorDto inputDescriptorDto() {
        return InputDescriptorDto.builder()
                .id(UUID.randomUUID().toString())
                .name("input_descriptor_name")
                .purpose("input_descriptor_purpose")
                .format(Map.of("vc+sd-jwt", formatAlgorithmDto()))
                .constraints(constraintDto())
                .build();
    }

    private static FormatAlgorithmDto formatAlgorithmDto() {
        return FormatAlgorithmDto.builder()
                .alg(List.of("ES256"))
                .keyBindingAlg(List.of("ES256"))
                .build();
    }

    private static ConstraintDto constraintDto() {
        return ConstraintDto.builder()
                .fields(List.of(fieldDto(List.of())))
                .build();
    }

    public static FieldDto fieldDto(List<String> path) {
        return FieldDto.builder()
                .path(isEmpty(path) ? List.of("$.test", "$.test2") : path)
                .id(UUID.randomUUID().toString())
                .name("field_name")
                .purpose("field_purpose")
                .build();
    }

    public static Map<String, FormatAlgorithmDto> formatAlgorithmDtoMap() {
        var formats = List.of("EC256");

        return new HashMap<>(Map.of("vc+sd-jwt", FormatAlgorithmDto.builder()
                .alg(formats)
                .keyBindingAlg(formats)
                .build()));
    }
}
