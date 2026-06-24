package ch.admin.bj.swiyu.verifier.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validation constraint that ensures a Map contains exactly one "default" key
 * with a non-null and non-blank String value.
 *
 * <p>Use this annotation on {@code Map<String, String>} fields that require
 * a mandatory "default" language entry for internationalization purposes.</p>
 */
@Documented
@Constraint(validatedBy = ContainsDefaultKeyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ContainsDefaultKey {

    String message() default "must contain exactly one 'default' key with a non-blank value";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

