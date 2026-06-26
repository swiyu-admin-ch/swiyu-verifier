package ch.admin.bj.swiyu.verifier.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;

/**
 * Validator implementation for {@link ContainsDefaultKey}.
 *
 * <p>Validates that a {@code Map<String, String>} contains exactly one "default" key
 * and that its value is non-null and non-blank.</p>
 */
public class ContainsDefaultKeyValidator implements ConstraintValidator<ContainsDefaultKey, Map<String, String>> {

    @Override
    public boolean isValid(Map<String, String> value, ConstraintValidatorContext context) {
        // Null maps are handled by @NotNull / @NotEmpty
        if (value == null) {
            return true;
        }

        var defaultCount = value.keySet().stream()
                .filter("default"::equals)
                .count();

        // Count "default" keys (should be exactly 1)
        if (defaultCount != 1) {
            return false;
        }

        // Validate the "default" value is non-null and non-blank
        String defaultValue = value.get("default");
        return defaultValue != null && !defaultValue.isBlank();
    }
}

