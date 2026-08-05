package ch.admin.bj.swiyu.verifier.dto.management;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = SessionNonceValidator.class)
public @interface SessionNonce {

    String message() default "must contain a session nonce";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
