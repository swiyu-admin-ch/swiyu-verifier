package ch.admin.bj.swiyu.verifier.api.definition;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class DcqlClaimPathValidator implements ConstraintValidator<DcqlClaimPath, List<Object>> {

    @Override
    public boolean isValid(List<Object> values, ConstraintValidatorContext context) {
        for(Object value : values) {
            if (!isValidPathOperation(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPathOperation(Object value) {
        return isSelectClaim(value)
                || isSelectArrayIndex(value)
                || isSelectAll(value);
    }

    /**
     * Any arbitrary string for selecting a JSON claim
     */
    private static boolean isSelectClaim(Object value) {
        return value instanceof String;
    }

    /**
     * A non-negative number for selecting a JSON Array Index
     */
    private static boolean isSelectArrayIndex(Object value) {
        return value instanceof Number number && number.intValue() >= 0;
    }

    /**
     * Null for selecting all elements in a JSON array
     */
    private static boolean isSelectAll(Object value) {
        return value == null;
    }
}
