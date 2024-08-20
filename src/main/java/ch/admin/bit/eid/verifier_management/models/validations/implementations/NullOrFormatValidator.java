package ch.admin.bit.eid.verifier_management.models.validations.implementations;

import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import ch.admin.bit.eid.verifier_management.models.validations.NullOrFormat;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;
import java.util.Set;

public class NullOrFormatValidator implements ConstraintValidator<NullOrFormat, Map<String, FormatAlgorithmDto>> {
    @Override
    public boolean isValid(Map<String, FormatAlgorithmDto> format, ConstraintValidatorContext constraintValidatorContext) {
        if (format == null) {
            return true;
        }

        boolean isValid = false;

        if (format.isEmpty()) {
            return false;
        }

        final Set<String> acceptedLDPFormats = Set.of("ldp_vp", "ldp_vc", "ldp", "jwt_vc", "jwt_vp");

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


