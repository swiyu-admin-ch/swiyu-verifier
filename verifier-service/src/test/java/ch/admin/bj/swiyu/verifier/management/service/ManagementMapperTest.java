/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.service;

import ch.admin.bj.swiyu.verifier.api.definition.*;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.service.management.ManagementMapper.toManagementResponseDto;
import static ch.admin.bj.swiyu.verifier.service.management.ManagementMapper.toPresentationDefinition;
import static ch.admin.bj.swiyu.verifier.management.test.fixtures.ApiFixtures.presentationDefinitionDto;
import static ch.admin.bj.swiyu.verifier.management.test.fixtures.ManagementFixtures.management;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.util.CollectionUtils.isEmpty;

class ManagementMapperTest {

    private static final String EXTERNAL_URL = "https://example.com";

    @Test
    void toManagementResponseDtoTest() {
        // GIVEN
        var mgmt = management();
        // WHEN
        var dto = toManagementResponseDto(mgmt, EXTERNAL_URL);
        // THEN
        assertNotNull(dto);
        assertEquals(mgmt.getId(), dto.id());
        assertThat(dto.requestNonce()).isNotBlank();
        assertEquals(mgmt.getState().toString(), dto.state().toString());
        assertNotNull(dto.presentationDefinition());
        assertEquals(mgmt.getRequestedPresentation().id(), dto.presentationDefinition().id());
        assertEquals(mgmt.getRequestedPresentation().name(), dto.presentationDefinition().name());
        assertEquals(mgmt.getRequestedPresentation().purpose(), dto.presentationDefinition().purpose());
        assertEqualFormat(mgmt.getRequestedPresentation().format(), dto.presentationDefinition().format());
        assertEqualInputDescriptors(mgmt.getRequestedPresentation().inputDescriptors(), dto.presentationDefinition().inputDescriptors());
        assertNull(dto.walletResponse());
        String expectedVerificationUrl = "%s/api/v1/request-object/%s".formatted(EXTERNAL_URL, mgmt.getId());
        assertEquals(expectedVerificationUrl, dto.verificationUrl());
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
        assertEquals(dto.id(), result.id());
        assertEquals(dto.name(), result.name());
        assertEquals(dto.purpose(), result.purpose());
        assertEqualFormat(result.format(), dto.format());
        assertEqualInputDescriptors(result.inputDescriptors(), dto.inputDescriptors());

    }


    @Test
    void toPresentationDefinition_NullDto() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            toPresentationDefinition(null);
        });
        assertEquals("PresentationDefinitionDto must not be null", exception.getMessage());
    }

    private void assertEqualInputDescriptors(List<PresentationDefinition.InputDescriptor> left, List<InputDescriptorDto> right) {
        if (isEmpty(left)) {
            assertThat(right).isNullOrEmpty();
            return;
        }
        assertThat(left).hasSameSizeAs(right);
        for (int i = 0; i < left.size(); i++) {
            var leftInputDescriptor = left.get(i);
            var rightInputDescriptor = right.get(i);
            assertEquals(leftInputDescriptor.id(), rightInputDescriptor.id());
            assertEquals(leftInputDescriptor.name(), rightInputDescriptor.name());
            assertEquals(leftInputDescriptor.purpose(), rightInputDescriptor.purpose());
            assertEqualConstraints(leftInputDescriptor.constraints(), rightInputDescriptor.constraints());
        }

    }

    private void assertEqualConstraints(PresentationDefinition.Constraint left, ConstraintDto right) {
        assertEquals(left.id(), right.id());
        assertEquals(left.name(), right.name());
        assertEquals(left.purpose(), right.purpose());
        assertEqualFormat(left.format(), right.format());
        assertEqualFields(left.fields(), right.fields());
    }

    private void assertEqualFields(List<PresentationDefinition.Field> left, List<FieldDto> right) {
        assertThat(left).hasSameSizeAs(right);
        for (int i = 0; i < left.size(); i++) {
            var leftField = left.get(i);
            var rightField = right.get(i);
            assertEquals(leftField.id(), rightField.id());
            assertEquals(leftField.name(), rightField.name());
            assertEquals(leftField.purpose(), rightField.purpose());
            assertEqualFilter(leftField.filter(), rightField.filter());
        }
    }

    private void assertEqualFilter(PresentationDefinition.Filter left, FilterDto right) {
        if (left == null) {
            assertNull(right);
            return;
        }
        assertEquals(left.constDescriptor(), right.constDescriptor());
        assertEquals(left.type(), right.type());
    }

    private void assertEqualFormat(Map<String, PresentationDefinition.FormatAlgorithm> left, Map<String, FormatAlgorithmDto> right) {
        if (isEmpty(left)) {
            assertThat(right).isNullOrEmpty();
            return;
        }
        assertThat(left).hasSameSizeAs(right);
        for (var entry : left.entrySet()) {
            var leftFormat = entry.getValue();
            var rightFormat = right.get(entry.getKey());
            assertThat(rightFormat).isNotNull();
            assertThat(rightFormat.alg()).hasSameSizeAs(leftFormat.alg());
            assertThat(rightFormat.keyBindingAlg()).hasSameSizeAs(leftFormat.keyBindingAlg());
            assertThat(rightFormat.alg()).containsExactlyElementsOf(leftFormat.alg());
            assertThat(rightFormat.keyBindingAlg()).containsExactlyElementsOf(leftFormat.keyBindingAlg());
        }
    }
}
