package ch.admin.bj.swiyu.verifier.api.management;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class AcceptedIssuerDidsOrTrustAnchorsNotEmptyValidator implements ConstraintValidator<AcceptedIssuerDidsOrTrustAnchorsNotEmpty, CreateVerificationManagementDto> {
    @Override
    public boolean isValid(CreateVerificationManagementDto dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true; // let @NotNull handle nulls if needed
        }
        List<?> acceptedIssuerDids = dto.acceptedIssuerDids();
        List<?> trustAnchors = dto.trustAnchors();

        boolean acceptedIssuerDidsEmpty = acceptedIssuerDids != null && acceptedIssuerDids.isEmpty();
        boolean trustAnchorsEmpty = trustAnchors != null && trustAnchors.isEmpty();

        // at least one of the lists must be non-empty
        if (acceptedIssuerDids == null &&  trustAnchors == null) {
            return false;
        }

        // when list is not null it must not be empty
        return !acceptedIssuerDidsEmpty && !trustAnchorsEmpty;
    }
}