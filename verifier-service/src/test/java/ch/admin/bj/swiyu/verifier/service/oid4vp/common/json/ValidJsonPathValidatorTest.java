/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp.common.json;

import ch.admin.bj.swiyu.verifier.common.util.json.ValidJsonPathValidator;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.service.oid4vp.RequestObjectMapper;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.PresentationDefinitionFixtures.presentationDefinitionWithFields;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ValidJsonPathValidatorTest {

    private ValidJsonPathValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new ValidJsonPathValidator();
        context = mock(ConstraintValidatorContext.class);
        when(context.buildConstraintViolationWithTemplate(Mockito.anyString())).thenReturn(mock(ConstraintValidatorContext.ConstraintViolationBuilder.class));
    }

    @Test
    void testNullValueIsValid() {
        assertTrue(validator.isValid(null, context), "Null values should be valid.");
    }

    @Test
    void testBlankValueIsInvalid() {
        assertFalse(validator.isValid("", context), "Blank values should be invalid.");
        assertFalse(validator.isValid("   ", context), "Whitespace-only values should be invalid.");
    }

    @Test
    void testValidJsonPath() {
        assertTrue(validator.isValid("$.foo.bar", context), "Basic JSONPath should be valid.");
        assertTrue(validator.isValid("$.foo.bar[0]", context), "Array indexing should be valid.");
        assertTrue(validator.isValid("$.foo.bar.bin", context), "Dot notation should be valid.");
    }

    @Test
    void testInvalidJsonPathWithRegularExpression() {
        assertFalse(validator.isValid("$.foo[?(@.bar =~ /[a-zA-Z0-9]+/)]", context), "Regular expressions should be invalid.");
        assertFalse(validator.isValid("$.foo.bar[?(@ =~ /^[a-zA-Z]+$/)]", context), "Regular expressions should be invalid.");
    }

    @Test
    void testInvalidJsonPathWithFilterExpression() {
        assertFalse(validator.isValid("$.foo.bar[?(@.bin < 10)]", context), "Filter expressions should be invalid.");
        assertFalse(validator.isValid("$.foo.bar[  ?(@.bin < 10)]", context), "Filter expressions with spaces should be invalid.");
        assertFalse(validator.isValid("$.foo.bar[?(@.baz == 'qux')]", context), "Filter expressions with conditions should be invalid.");
    }

    @Test
    void testInvalidJsonPathSyntax() {
        assertFalse(validator.isValid("$.foo.bar[]", context), "Empty brackets should be invalid.");
        assertFalse(validator.isValid("$.foo.bar[baz]", context), "Non-numeric array indexing should be invalid.");
    }

    @Test
    void testCorrectValidationMessageForInvalidJsonPath() {
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(Mockito.anyString())).thenReturn(builder);

        validator.isValid("$.foo.bar[0", context);

        // verify that default validation message has not been overwritten (default message should be used)
        verify(context, times(0)).buildConstraintViolationWithTemplate("JsonPath filter expressions are not allowed");
    }

    @Test
    void testCorrectValidationMessageForUsingProhibitedFilterExpression() {
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(Mockito.anyString())).thenReturn(builder);

        validator.isValid("$.foo[?(@.bar =~ /[a-zA-Z0-9]+/)]", context);

        // verify that default validation message has been overwritten because of using prohibited filter expression (regex in this case)
        verify(context, times(1)).buildConstraintViolationWithTemplate("JsonPath filter expressions are not allowed");
    }

    @Test
    void testPresentationDefinitionValidation_violationExpected() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var myvalidator = factory.getValidator();
            var presentationDefinition = createPresentationDefinition();
            var violations = myvalidator.validate(presentationDefinition);
            // uncomment to see the exact violation errors
            // violations.forEach(violation ->
            // System.out.println(violation.getPropertyPath() + "': " + violation.getMessage())
            // );
            assertFalse(violations.isEmpty(), "Validation should fail with errors.");
        }
    }

    @Test
    void testPresentationDefinitionDtoValidation_violationExpected() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var myvalidator = factory.getValidator();
            var presentationDefinitionDto = RequestObjectMapper.toPresentationDefinitionDto(createPresentationDefinition());
            var violations = myvalidator.validate(presentationDefinitionDto);
            // uncomment to see the exact violation errors
            // violations.forEach(violation ->
            // System.out.println(violation.getPropertyPath() + "': " + violation.getMessage())
            // );
            assertFalse(violations.isEmpty(), "Validation should fail with errors.");
        }
    }


    private PresentationDefinition createPresentationDefinition() {
        HashMap<String, PresentationDefinition.FormatAlgorithm> formats = new HashMap<>();
        formats.put("vc+sd-jwt", PresentationDefinition.FormatAlgorithm.builder()
                .keyBindingAlg(List.of("ES256"))
                .alg(List.of("ES256"))
                .build());

        return presentationDefinitionWithFields(
                UUID.randomUUID(),
                List.of(
                        PresentationDefinition.Field.builder().path(List.of("$.vct")).filter(PresentationDefinition.Filter.builder().type("string").constDescriptor("testCredentialType").build()).build(),
                        PresentationDefinition.Field.builder().path(List.of("$[?(@.last_name=~/^((x+x+x+)+)*y/)]")).build()
                ),
                null,
                formats
        );
    }
}
