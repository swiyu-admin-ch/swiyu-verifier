package ch.admin.bit.eid.verifier_management.mocks;

import ch.admin.bit.eid.verifier_management.models.dto.ConstraintDto;
import ch.admin.bit.eid.verifier_management.models.dto.CreateVerificationManagementDto;
import ch.admin.bit.eid.verifier_management.models.dto.FieldDto;
import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import ch.admin.bit.eid.verifier_management.models.dto.InputDescriptorDto;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hibernate.internal.util.collections.CollectionHelper.isEmpty;

public class VerificationRequestMock {

    public static CreateVerificationManagementDto create() {
        return CreateVerificationManagementDto.builder()
                .jwtSecuredAuthorizationRequest(false)
                .presentationDefinition(getPresentationDefinitionMock())
                .build();
    }

    public static CreateVerificationManagementDto createMinimal(boolean isJWTSecured) {
        PresentationDefinitionDto pres = PresentationDefinitionDto.builder()
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

    public static PresentationDefinitionDto getPresentationDefinitionMock() {
        return PresentationDefinitionDto
                .builder()
                .id(UUID.randomUUID().toString())
                .name("presentation_definition_name")
                .purpose("presentation_definition_purpose")
                .inputDescriptors(List.of(getInputDescriptorMock()))
                .build();
    }

    public static InputDescriptorDto getInputDescriptorMock() {
        return InputDescriptorDto.builder()
                .id(UUID.randomUUID().toString())
                .name("input_descriptor_name")
                .purpose("input_descriptor_purpose")
                .format(Map.of("vc+sd-jwt", createFormatAlgorithmDto()))
                .constraints(createConstraintDto())
                .build();
    }

    private static FormatAlgorithmDto createFormatAlgorithmDto() {
        return FormatAlgorithmDto.builder()
                .alg(List.of("ES256"))
                .keyBindingAlg(List.of("ES256"))
                .build();
    }

    private static ConstraintDto createConstraintDto() {
        return ConstraintDto.builder()
                .fields(List.of(getField(List.of())))
                .build();
    }

    public static FieldDto getField(List<String> path) {
        return FieldDto.builder()
                .path(isEmpty(path) ? List.of("$.test", "$.test2") : path)
                .id(UUID.randomUUID().toString())
                .name("field_name")
                .purpose("field_purpose")
                .build();
    }
}
