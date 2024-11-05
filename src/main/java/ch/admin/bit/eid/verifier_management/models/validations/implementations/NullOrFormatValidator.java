package ch.admin.bit.eid.verifier_management.models.validations.implementations;

import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import ch.admin.bit.eid.verifier_management.models.validations.NullOrFormat;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;

public class NullOrFormatValidator implements ConstraintValidator<NullOrFormat, Map<String, FormatAlgorithmDto>> {

    @Override
    public boolean isValid(Map<String, FormatAlgorithmDto> format, ConstraintValidatorContext constraintValidatorContext) {
        if (format == null) {
            return true;
        }

        if (format.isEmpty()) {
            return false;
        }

        return format.entrySet().stream().allMatch(this::isValidSDJWTFormat);
    }

    private boolean isValidSDJWTFormat(Map.Entry<String, FormatAlgorithmDto> entry) {
        final String acceptedSDJWTSFormat = "vc+sd-jwt";

        return acceptedSDJWTSFormat.equals(entry.getKey())
                && entry.getValue().getAlg() != null && !entry.getValue().getAlg().isEmpty()
                && entry.getValue().getKeyBindingAlg() != null;
    }
}
