package ch.admin.bj.swiyu.verifier.dto.management;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TrustAnchorDtoTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void whenDidIsBlank_thenValidationFails() {
        TrustAnchorDto dto = new TrustAnchorDto("", "https://trust-reg.trust-infra.swiyu-int.admin.ch");
        Set<ConstraintViolation<TrustAnchorDto>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().map(ConstraintViolation::getMessage))
                .contains("must not be blank");
    }

    @Test
    void whenTrustRegistryUriIsBlank_thenValidationFails() {
        TrustAnchorDto dto = new TrustAnchorDto("did:example:12345", "");
        Set<ConstraintViolation<TrustAnchorDto>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().map(ConstraintViolation::getMessage))
                .contains("must not be blank");
    }

    @Test
    void whenTrustRegistryUriIsNotHttps_thenValidationFails() {
        TrustAnchorDto dto = new TrustAnchorDto("did:example:12345", "http://trust-reg.trust-infra.swiyu-int.admin.ch");
        Set<ConstraintViolation<TrustAnchorDto>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().map(ConstraintViolation::getMessage))
                .contains("Trust Registry URL must utilize https");
    }

    @Test
    void whenTrustRegistryUriIsNotValidUrl_thenValidationFails() {
        TrustAnchorDto dto = new TrustAnchorDto("did:example:12345", "not-a-valid-url");
        Set<ConstraintViolation<TrustAnchorDto>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().map(ConstraintViolation::getMessage))
                .contains("must be a valid URL");
    }

    @Test
    void whenDtoIsValid_thenValidationPasses() {
        TrustAnchorDto dto = new TrustAnchorDto(
                "did:example:12345",
                "https://trust-reg.trust-infra.swiyu-int.admin.ch"
        );
        Set<ConstraintViolation<TrustAnchorDto>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }
}