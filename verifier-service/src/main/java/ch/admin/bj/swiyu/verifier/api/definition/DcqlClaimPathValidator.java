package ch.admin.bj.swiyu.verifier.api.definition;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class DcqlClaimPathValidator implements ConstraintValidator<DcqlClaimPath, List<Object>> {

    @Override
    public boolean isValid(List<Object> values, ConstraintValidatorContext context) {
        for(Object value : values) {
            switch (value) {
                case null -> {
                    // null allowed for selecting every element in an array
                }
                case Number number when number.intValue() >= 0 -> {
                    // Number allowed to select a certain element of an array
                }
                case Number number when number.intValue() < 0 -> {
                    // Array index less than 0 not allowed
                    return false;
                }
                case String s -> {
                    // String allowed for selecting json element
                }
                default -> {
                    // Other values like boolean are not accepted
                    return false;
                }
            }
        }
        return true;
    }
}
