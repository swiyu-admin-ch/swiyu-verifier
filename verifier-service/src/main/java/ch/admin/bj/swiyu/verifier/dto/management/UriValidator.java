package ch.admin.bj.swiyu.verifier.dto.management;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;

public class UriValidator implements ConstraintValidator<ValidUri, URI> {

    @Override
    public boolean isValid(URI value, ConstraintValidatorContext context) {

        // Null values must be handled by @NotNull annotation
        if (value == null) {
            return true;
        }

        return value.isAbsolute()
            && "https".equalsIgnoreCase(value.getScheme())
            && value.getHost() != null;
    }
}
