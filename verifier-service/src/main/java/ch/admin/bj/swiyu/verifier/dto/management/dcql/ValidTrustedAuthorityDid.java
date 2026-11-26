package ch.admin.bj.swiyu.verifier.dto.management.dcql;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is a syntactically valid DID of the {@code did:tdw} or {@code did:webvh} method,
 * e.g. {@code did:webvh:<scid>:www.example.com}.
 * <p>
 * The value must be a bare DID: DID URL parameters, paths and fragments (such as {@code #assert-key-01})
 * are rejected, because trusted authorities are matched against the issuer DID only.
 * {@code null} values are considered valid and must be rejected by {@link jakarta.validation.constraints.NotEmpty}.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTrustedAuthorityDidValidator.class)
public @interface ValidTrustedAuthorityDid {

    String message() default "must be a valid did:tdw or did:webvh DID (e.g. did:webvh:<scid>:www.example.com)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
