package ch.admin.bit.eid.verifier_management.models.validations;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class NullOrValuesValidator implements ConstraintValidator<NullOrValues, String> {

    private String[] allowedValues;

    @Override
    public void initialize(NullOrValues constraintAnnotation) {
        this.allowedValues = constraintAnnotation.values();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        return value == null || List.of(allowedValues).contains(value);
    }
}
