package ch.admin.bj.swiyu.verifier.dto.definition;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NullOrValidFormatAlgorithmValidatorTest {

    private NullOrValidFormatAlgorithmValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new NullOrValidFormatAlgorithmValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @Test
    void testIsValid_NullFormat() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void testIsValid_EmptyFormat() {
        assertFalse(validator.isValid(Map.of(), context));
    }

    @Test
    void testIsValid_ValidFormat() {
        // GIVEN
        var formatMap = Map.of("vc+sd-jwt", new FormatAlgorithmDto(
                List.of("alg1", "alg2"),
                List.of("keyBindingAlg1"))
        );
        // WHEN / THEN
        assertTrue(validator.isValid(formatMap, context));
    }

    @Test
    void testIsValid_InvalidFormatKey() {
        // GIVEN
        var formatMap = Map.of("invalid-key", new FormatAlgorithmDto(
                List.of("alg1", "alg2"),
                List.of("keyBindingAlg1"))
        );
        // WHEN / THEN
        assertFalse(validator.isValid(formatMap, context));
    }

    @Test
    void testIsValid_InvalidFormatValue() {
        // GIVEN
        var formatMap = Map.of("invalid-key", new FormatAlgorithmDto(emptyList(), null));
        // WHEN / THEN
        assertFalse(validator.isValid(formatMap, context));
    }
}
