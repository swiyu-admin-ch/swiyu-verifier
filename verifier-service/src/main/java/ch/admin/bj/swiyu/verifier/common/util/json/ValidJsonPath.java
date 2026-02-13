package ch.admin.bj.swiyu.verifier.common.util.json;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = {ValidJsonPathValidator.class, ValidJsonPathListValidator.class})
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidJsonPath {

    String message() default "Must be a json path";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
