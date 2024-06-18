package ch.admin.bit.eid.oid4vp.model.validations;

import ch.admin.bit.eid.oid4vp.model.validations.impl.SubmissionRequirementValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Constraint(validatedBy = SubmissionRequirementValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSubmissionRequirement {
    String message() default "Form and FormNested cannot be set at the same time";
    Class<?>[] groups() default { };
    Class<? extends Payload>[] payload() default { };
}
