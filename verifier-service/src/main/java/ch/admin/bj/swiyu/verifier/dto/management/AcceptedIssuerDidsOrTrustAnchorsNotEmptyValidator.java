package ch.admin.bj.swiyu.verifier.dto.management;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;
import java.util.Objects;

public class AcceptedIssuerDidsOrTrustAnchorsNotEmptyValidator implements ConstraintValidator<AcceptedIssuerDidsOrTrustAnchorsNotEmpty, CreateVerificationManagementDto> {
    @Override
    public boolean isValid(CreateVerificationManagementDto dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true; // let @NotNull handle nulls if needed
        }
        List<?> acceptedIssuerDids = dto.acceptedIssuerDids();
        List<?> trustAnchors = dto.trustAnchors();

        boolean acceptedIssuerDIDsValid = acceptedIssuerDids == null || (!acceptedIssuerDids.isEmpty() && acceptedIssuerDids.stream().noneMatch(Objects::isNull));
        boolean trustAnchorsValid = trustAnchors == null || (!trustAnchors.isEmpty() && trustAnchors.stream().noneMatch(Objects::isNull));

        // at least one of the lists must be non-empty
        if (acceptedIssuerDids == null &&  trustAnchors == null) {
            return false;
        }

        // when list is not null it must not be empty
        return acceptedIssuerDIDsValid && trustAnchorsValid;
    }
}