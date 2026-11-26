package ch.admin.bj.swiyu.verifier.dto.management.dcql;

import ch.admin.bj.swiyu.didresolveradapter.DidHelper;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for {@link ValidTrustedAuthorityDid}.
 * <p>
 * The syntax check is delegated to {@link DidHelper#isValidDid(String)}, so a value is only accepted if
 * it is a syntactically valid swiyu DID
 * <p>
 */
public class ValidTrustedAuthorityDidValidator implements ConstraintValidator<ValidTrustedAuthorityDid, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null must be handled by @NotEmpty
        if (value == null) {
            return true;
        }

        return DidHelper.isValidDid(value);
    }
}
