package ch.admin.bj.swiyu.verifier.oid4vp.common.json;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidJsonPathValidatorTest {

    private ValidJsonPathValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new ValidJsonPathValidator();
        context = Mockito.mock(ConstraintValidatorContext.class);
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
        assertFalse(validator.isValid("$.foo.bar[^[a-zA-Z0-9]+$]", context), "Regular expressions should be invalid.");
        assertFalse(validator.isValid("$.foo.bar[([a-zA-Z]+)*]", context), "Regular expressions should be invalid.");
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
}
