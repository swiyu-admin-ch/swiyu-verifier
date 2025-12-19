package ch.admin.bj.swiyu.verifier.dto.management;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = AcceptedIssuerDidsOrTrustAnchorsNotEmptyValidator.class)
@Target({ TYPE })
@Retention(RUNTIME)
public @interface AcceptedIssuerDidsOrTrustAnchorsNotEmpty {
    String message() default "Either acceptedIssuerDids or trustAnchors must be set and cannot be empty.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}