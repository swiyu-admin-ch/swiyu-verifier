package ch.admin.bit.eid.verifier_management.models.validations.implementations;

import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import ch.admin.bit.eid.verifier_management.models.validations.NullOrFormat;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class NullOrFormatValidator implements ConstraintValidator<NullOrFormat, HashMap<String, FormatAlgorithmDto>> {
    @Override
    public boolean isValid(HashMap<String, FormatAlgorithmDto> format, ConstraintValidatorContext constraintValidatorContext) {
        if (format == null) {
            return true;
        }

        boolean isValid = false;

        if (format.isEmpty()) {
            return false;
        }

        Set<String> acceptedLDPFormats = new HashSet<>(Arrays.asList("ldp_vp", "ldp_vc", "ldp"));

        for (var entry : format.entrySet()) {
            isValid = acceptedLDPFormats.contains(entry.getKey())
                    && (entry.getValue().getProofType() != null
                    && !entry.getValue().getProofType().isEmpty());

            if (!isValid) {
                break;
            }
        }

        return isValid;
    }
}


