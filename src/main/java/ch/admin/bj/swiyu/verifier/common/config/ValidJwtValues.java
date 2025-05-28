package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = ValidJwtValuesValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidJwtValues {
    String message() default "Invalid jwt values provided. Only ES256 is supported.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}