package ch.admin.bj.swiyu.verifier.dto.management;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = RedirectUriValidator.class)
public @interface RedirectUri {

    String message() default "must be an absolute URI and contain a session_nonce parameter";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
