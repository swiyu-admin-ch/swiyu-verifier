package ch.admin.bit.eid.verifier_management.models.validations.implementations;

import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import ch.admin.bit.eid.verifier_management.models.validations.NullOrFormat;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.Map;

public class NullOrFormatValidator implements ConstraintValidator<NullOrFormat, Map<String, FormatAlgorithmDto>> {

    private static boolean isAlgListNotEmptyOrNull(List<String> list) {
        return list != null && !list.isEmpty();
    }

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

        // TODO check if supported & secure algs
        return acceptedSDJWTSFormat.equals(entry.getKey())
                && isAlgListNotEmptyOrNull(entry.getValue().getAlg())
                && isAlgListNotEmptyOrNull(entry.getValue().getKeyBindingAlg());
    }
}
