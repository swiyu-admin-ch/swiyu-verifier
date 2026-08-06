package ch.admin.bj.swiyu.verifier.dto.management;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = UriValidator.class)
public @interface ValidUri {

    String message() default "must be a valid URI";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
