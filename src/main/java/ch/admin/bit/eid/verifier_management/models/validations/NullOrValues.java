package ch.admin.bit.eid.verifier_management.models.validations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Constraint(validatedBy = NullOrValuesValidator.class)
@Target( { ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface NullOrValues {

    String message() default "Limit must be null or required or preferred";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String[] values();
}
