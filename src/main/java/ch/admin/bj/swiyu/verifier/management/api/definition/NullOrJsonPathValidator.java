package ch.admin.bj.swiyu.verifier.management.api.definition;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class NullOrJsonPathValidator implements ConstraintValidator<NullOrJsonPath, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {

        if (value == null) {
            return true;
        }

        if (value.isBlank()) {
            try {
                JsonPath.compile(value);
            } catch (InvalidPathException e) {
                return false;
            }
        }

        return false;
    }
}
