package ch.admin.bj.swiyu.verifier.common.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ContainsDefaultKeyValidator}.
 * <p>Tests the validation logic that ensures a {@code Map<String, String>}
 * contains exactly one "default" key with a non-blank value.</p>
 */
class ContainsDefaultKeyValidatorTest {

    private final String deText = "Deutscher Text";
    private final String frText = "Texte français";

    private ContainsDefaultKeyValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new ContainsDefaultKeyValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @Test
    void isValid_withNullMap_shouldReturnTrue() {
        Map<String, String> map = null;
        boolean result = validator.isValid(map, context);
        assertThat(result).isTrue();
    }

    @Test
    void isValid_withDefaultKeyAndNonBlankValue_shouldReturnTrue() {
        Map<String, String> map = Map.of("default", "Some value");
        boolean result = validator.isValid(map, context);
        assertThat(result).isTrue();
    }

    @Test
    void isValid_withDefaultKeyAndOtherKeys_shouldReturnTrue() {
        Map<String, String> map = Map.of(
                "default", "English text",
                "de-ch", deText,
                "fr-ch", frText
        );
        boolean result = validator.isValid(map, context);
        assertThat(result).isTrue();
    }

    @Test
    void isValid_withMissingDefaultKey_shouldReturnFalse() {
        Map<String, String> map = Map.of(
                "de-ch", deText,
                "fr-ch", frText
        );
        boolean result = validator.isValid(map, context);
        assertThat(result).isFalse();
    }

    @Test
    void isValid_withEmptyMap_shouldReturnFalse() {
        Map<String, String> map = Map.of();
        boolean result = validator.isValid(map, context);
        assertThat(result).isFalse();
    }

    @Test
    void isValid_withNullDefaultValue_shouldReturnFalse() {
        Map<String, String> map = new HashMap<>();
        map.put("default", null);
        map.put("de-ch", deText);
        boolean result = validator.isValid(map, context);
        assertThat(result).isFalse();
    }

    @Test
    void isValid_withEmptyDefaultValue_shouldReturnFalse() {
        Map<String, String> map = Map.of(
                "default", "",
                "de-ch", deText
        );
        boolean result = validator.isValid(map, context);
        assertThat(result).isFalse();
    }

    @Test
    void isValid_withBlankDefaultValue_shouldReturnFalse() {
        Map<String, String> map = Map.of(
                "default", "   \t\n  ",
                "de-ch", deText
        );
        boolean result = validator.isValid(map, context);
        assertThat(result).isFalse();
    }

    @Test
    void isValid_withOnlyWhitespaceDefaultValue_shouldReturnFalse() {
        Map<String, String> map = Map.of("default", " ");
        boolean result = validator.isValid(map, context);
        assertThat(result).isFalse();
    }

    @Test
    void isValid_withValidDefaultAndNoOtherKeys_shouldReturnTrue() {
        Map<String, String> map = Map.of("default", "Just the default value");
        boolean result = validator.isValid(map, context);
        assertThat(result).isTrue();
    }
}

