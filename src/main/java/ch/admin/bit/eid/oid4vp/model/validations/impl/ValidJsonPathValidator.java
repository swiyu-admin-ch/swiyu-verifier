package ch.admin.bit.eid.oid4vp.model.validations.impl;

import ch.admin.bit.eid.oid4vp.model.validations.ValidJsonPath;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidJsonPathValidator implements ConstraintValidator<ValidJsonPath, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {

        if (value == null) {
            return true;
        }

        if (!value.isBlank()) {
            try {
                JsonPath.compile(value);
                return true;
            } catch (InvalidPathException e) {
                return false;
            }
        }

        return false;
    }
}
