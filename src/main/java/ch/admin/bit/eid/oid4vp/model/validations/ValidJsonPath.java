package ch.admin.bit.eid.oid4vp.model.validations;

import ch.admin.bit.eid.oid4vp.model.validations.impl.ValidJsonPathValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = ValidJsonPathValidator.class)
@Target( { ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidJsonPath {

    String message() default "Must be a json path";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
