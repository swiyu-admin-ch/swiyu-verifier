package ch.admin.bit.eid.verifier_management.models.validations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = NullOrJsonPathValidator.class)
@Target( { ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface NullOrJsonPath {

    String message() default "Must be a json path";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
