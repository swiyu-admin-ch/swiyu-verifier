package ch.admin.bj.swiyu.verifier.management.api.definition;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;

class NullOrValidFormatAlgorithmValidator implements ConstraintValidator<NullOrValidFormatAlgorithm, Map<String, FormatAlgorithmDto>> {

    @Override
    public boolean isValid(Map<String, FormatAlgorithmDto> format, ConstraintValidatorContext constraintValidatorContext) {
        if (format == null) {
            return true;
        }

        if (format.isEmpty()) {
            return false;
        }

        return format.entrySet().stream().allMatch(this::isValidSdjwtFormat);
    }

    private boolean isValidSdjwtFormat(Map.Entry<String, FormatAlgorithmDto> entry) {
        final String acceptedSDJWTSFormat = "vc+sd-jwt";

        return acceptedSDJWTSFormat.equals(entry.getKey())
                && entry.getValue().getAlg() != null && !entry.getValue().getAlg().isEmpty()
                && entry.getValue().getKeyBindingAlg() != null;
    }
}
