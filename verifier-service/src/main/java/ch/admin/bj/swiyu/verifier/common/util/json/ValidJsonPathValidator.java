package ch.admin.bj.swiyu.verifier.common.util.json;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidJsonPathValidator implements ConstraintValidator<ValidJsonPath, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        return JsonPathValidator.isValidJsonPath(value, constraintValidatorContext);
    }
}
