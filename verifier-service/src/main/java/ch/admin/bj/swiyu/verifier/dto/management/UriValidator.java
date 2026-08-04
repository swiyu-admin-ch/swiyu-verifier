package ch.admin.bj.swiyu.verifier.dto.management;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;

public class UriValidator implements ConstraintValidator<ValidUri, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        // Null or Blank values must be handled by @NotNull or @NotBlank annotations
        if (value == null || value.isBlank()) {
            return true;
        }

        try {
            new URI(value);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
