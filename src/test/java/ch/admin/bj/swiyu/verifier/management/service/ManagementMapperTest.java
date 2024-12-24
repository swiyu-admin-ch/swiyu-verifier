package ch.admin.bj.swiyu.verifier.management.service;

import ch.admin.bj.swiyu.verifier.management.api.definition.*;
import ch.admin.bj.swiyu.verifier.management.domain.management.PresentationDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.management.service.ManagementMapper.toManagementResponseDto;
import static ch.admin.bj.swiyu.verifier.management.service.ManagementMapper.toPresentationDefinition;
import static ch.admin.bj.swiyu.verifier.management.test.fixtures.ApiFixtures.presentationDefinitionDto;
import static ch.admin.bj.swiyu.verifier.management.test.fixtures.ManagementFixtures.management;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ManagementMapperTest {

    private static final String OID4VP_URL = "https://example.com";

    @Test
    void toManagementResponseDtoTest() {
        // GIVEN
        var mgmt = management();
        // WHEN
        var dto = toManagementResponseDto(mgmt, OID4VP_URL);
        // THEN
        assertNotNull(dto);
        assertEquals(mgmt.getId(), dto.getId());
        assertThat(dto.getRequestNonce()).isNotBlank();
        assertEquals(mgmt.getState().toString(), dto.getState().toString());
        assertNotNull(dto.getPresentationDefinition());
        assertEquals(mgmt.getRequestedPresentation().id(), dto.getPresentationDefinition().getId());
        assertEquals(mgmt.getRequestedPresentation().name(), dto.getPresentationDefinition().getName());
        assertEquals(mgmt.getRequestedPresentation().purpose(), dto.getPresentationDefinition().getPurpose());
        assertEqualFormat(mgmt.getRequestedPresentation().format(), dto.getPresentationDefinition().getFormat());
        assertEqualInputDescriptors(mgmt.getRequestedPresentation().inputDescriptors(), dto.getPresentationDefinition().getInputDescriptors());
        assertNull(dto.getWalletResponse());
        String expectedVerificationUrl = "%s/request-object/%s".formatted(OID4VP_URL, mgmt.getId());
        assertEquals(expectedVerificationUrl, dto.getVerificationUrl());
    }

    @Test
    void toManagementResponseDtoTest_NullManagement() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            toManagementResponseDto(null, "http://example.com");
        });

        assertEquals("Management must not be null", exception.getMessage());
    }

    @Test
    void toPresentationDefinitionTest() {
        // GIVEN
        var dto = presentationDefinitionDto();
        // WHEN
        var result = toPresentationDefinition(dto);
        // THEN
        assertNotNull(result);
        assertEquals(dto.getId(), result.id());
        assertEquals(dto.getName(), result.name());
        assertEquals(dto.getPurpose(), result.purpose());
        assertEqualFormat(result.format(), dto.getFormat());
        assertEqualInputDescriptors(result.inputDescriptors(), dto.getInputDescriptors());

    }


    @Test
    void toPresentationDefinition_NullDto() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            toPresentationDefinition(null);
        });
        assertEquals("PresentationDefinitionDto must not be null", exception.getMessage());
    }


    private void assertEqualInputDescriptors(List<PresentationDefinition.InputDescriptor> left, List<InputDescriptorDto> right) {
        if (left == null) {
            assertNull(right);
            return;
        }
        assertThat(left).hasSameSizeAs(right);
        for (int i = 0; i < left.size(); i++) {
            var leftInputDescriptor = left.get(i);
            var rightInputDescriptor = right.get(i);
            assertEquals(leftInputDescriptor.id(), rightInputDescriptor.getId());
            assertEquals(leftInputDescriptor.name(), rightInputDescriptor.getName());
            assertEquals(leftInputDescriptor.purpose(), rightInputDescriptor.getPurpose());
            assertEqualConstraints(leftInputDescriptor.constraints(), rightInputDescriptor.getConstraints());
        }

    }

    private void assertEqualConstraints(PresentationDefinition.Constraint left, ConstraintDto right) {
        assertEquals(left.id(), right.getId());
        assertEquals(left.name(), right.getName());
        assertEquals(left.purpose(), right.getPurpose());
        assertEqualFormat(left.format(), right.getFormat());
        assertEqualFields(left.fields(), right.getFields());
    }

    private void assertEqualFields(List<PresentationDefinition.Field> left, List<FieldDto> right) {
        assertThat(left).hasSameSizeAs(right);
        for (int i = 0; i < left.size(); i++) {
            var leftField = left.get(i);
            var rightField = right.get(i);
            assertEquals(leftField.id(), rightField.getId());
            assertEquals(leftField.name(), rightField.getName());
            assertEquals(leftField.purpose(), rightField.getPurpose());
            assertEqualFilter(leftField.filter(), rightField.getFilter());
        }
    }

    private void assertEqualFilter(PresentationDefinition.Filter left, FilterDto right) {
        if (left == null) {
            assertNull(right);
            return;
        }
        assertEquals(left.constDescriptor(), right.getConstDescriptor());
        assertEquals(left.type(), right.getType());
    }

    private void assertEqualFormat(Map<String, PresentationDefinition.FormatAlgorithm> left, Map<String, FormatAlgorithmDto> right) {
        if (left == null) {
            assertNull(right);
            return;
        }
        assertThat(left).hasSameSizeAs(right);
        for (var entry : left.entrySet()) {
            var leftFormat = entry.getValue();
            var rightFormat = right.get(entry.getKey());
            assertThat(rightFormat).isNotNull();
            assertThat(rightFormat.getAlg()).hasSameSizeAs(leftFormat.alg());
            assertThat(rightFormat.getKeyBindingAlg()).hasSameSizeAs(leftFormat.keyBindingAlg());
            assertThat(rightFormat.getAlg()).containsExactlyElementsOf(leftFormat.alg());
            assertThat(rightFormat.getKeyBindingAlg()).containsExactlyElementsOf(leftFormat.keyBindingAlg());
        }
    }

//
//    @Test
//    void testToDto() {
//        InputDescriptor model = InputDescriptor.builder()
//                .id(UUID.randomUUID().toString())
//                .name("Test Name")
//                .purpose("Test Purpose")
//                .constraints(ConstraintDto.builder().build())
//                .format(ApiFixtures.createFormatAlgorithmDto())
//                .build();
//
//        InputDescriptorDto dto = InputDescriptorMapper.toDto(model);
//
//        assertNotNull(dto);
//        assertEquals(model.getId(), dto.getId());
//        assertEquals(model.getName(), dto.getName());
//        assertEquals(model.getPurpose(), dto.getPurpose());
//        assertEquals(model.getConstraints(), dto.getConstraints());
//        assertEquals(model.getFormat(), dto.getFormat());
//    }
//
//    @Test
//    void testToDto_NullModel() {
//        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
//            InputDescriptorMapper.toDto(null);
//        });
//
//        assertEquals("InputDescriptor cannot be null", exception.getMessage());
//    }
//
//    @Test
//    void testToDTOs() {
//        InputDescriptor model1 = InputDescriptor.builder()
//                .id(UUID.randomUUID().toString())
//                .name("Test Name 1")
//                .purpose("Test Purpose 1")
//                .constraints(ConstraintDto.builder().build())
//                .format(ApiFixtures.createFormatAlgorithmDto())
//                .build();
//
//        InputDescriptor model2 = InputDescriptor.builder()
//                .id(UUID.randomUUID().toString())
//                .name("Test Name 2")
//                .purpose("Test Purpose 2")
//                .constraints(ConstraintDto.builder().build())
//                .format(ApiFixtures.createFormatAlgorithmDto())
//                .build();
//
//        List<InputDescriptorDto> dtos = InputDescriptorMapper.toDTOs(List.of(model1, model2));
//
//        assertNotNull(dtos);
//        assertEquals(2, dtos.size());
//        assertEquals(model1.getId(), dtos.get(0).getId());
//        assertEquals(model2.getId(), dtos.get(1).getId());
//    }

//    @Test
//    void toDto_ShouldThrowException_WhenResponseDataIsNull() {
//        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
//            ResponseDataMapper.toDto(null);
//        });
//
//        assertEquals("Response data cannot be null", exception.getMessage());
//    }
//
//    @Test
//    void toDto_ShouldMapFieldsCorrectly_WhenResponseDataIsValid() {
//        ResponseData responseData = new ResponseData();
//        responseData.setErrorCode(VerificationErrorResponseCodeDto.CREDENTIAL_INVALID);
//        responseData.setErrorDescription("Not Found");
//        responseData.setCredentialSubjectData("{\"key\":\"value\"}");
//
//        ResponseDataDto responseDataDto = ResponseDataMapper.toDto(responseData);
//
//        assertNotNull(responseDataDto);
//        assertEquals(VerificationErrorResponseCodeDto.CREDENTIAL_INVALID, responseDataDto.getErrorCode());
//        assertEquals("Not Found", responseDataDto.getErrorDescription());
//        assertNotNull(responseDataDto.getCredentialSubjectData());
//        assertEquals("value", responseDataDto.getCredentialSubjectData().get("key"));
//    }
//
//    @Test
//    void toDto_ShouldHandleNullCredentialSubjectData() {
//        ResponseData responseData = new ResponseData();
//        responseData.setErrorCode(VerificationErrorResponseCodeDto.CREDENTIAL_INVALID);
//        responseData.setErrorDescription("Not Found");
//        responseData.setCredentialSubjectData(null);
//
//        ResponseDataDto responseDataDto = ResponseDataMapper.toDto(responseData);
//
//        assertNotNull(responseDataDto);
//        assertEquals(VerificationErrorResponseCodeDto.CREDENTIAL_INVALID, responseDataDto.getErrorCode());
//        assertEquals("Not Found", responseDataDto.getErrorDescription());
//        assertNull(responseDataDto.getCredentialSubjectData());
//    }
}
