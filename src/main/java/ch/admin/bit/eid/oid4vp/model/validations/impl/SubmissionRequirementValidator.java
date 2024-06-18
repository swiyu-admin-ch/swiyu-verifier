package ch.admin.bit.eid.oid4vp.model.validations.impl;

import ch.admin.bit.eid.oid4vp.model.SubmissionRequirement;
import ch.admin.bit.eid.oid4vp.model.validations.ValidSubmissionRequirement;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import static java.util.Objects.isNull;

public class SubmissionRequirementValidator implements ConstraintValidator<ValidSubmissionRequirement, SubmissionRequirement> {
    @Override
    public boolean isValid(SubmissionRequirement submissionRequirement, ConstraintValidatorContext constraintValidatorContext) {
        if (submissionRequirement != null && (isNull(submissionRequirement.getFrom()) && !isNull(submissionRequirement.getFromNested()) && !submissionRequirement.getFromNested().isEmpty())
                || (!isNull(submissionRequirement.getFrom()) && isNull(submissionRequirement.getFromNested()))) {
            return true;
        }
        return false;
    }
}
