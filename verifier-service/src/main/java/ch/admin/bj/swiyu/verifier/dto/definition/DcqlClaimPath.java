package ch.admin.bj.swiyu.verifier.dto.definition;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = DcqlClaimPathValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DcqlClaimPath {

    String message() default "Invalid Dcql path value; only supporting String, Number and null";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
