package ch.admin.bj.swiyu.verifier.dto.management.dcql;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DcqlClaimDtoTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = createValidator();
    }

    private static Validator createValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator();
        }
    }

    @Test
    void whenDtoIsValid_thenValidationPasses() {
        DcqlClaimDto dto = new DcqlClaimDto(
                "claim_1",
                List.of("credentialSubject", "given_name"),
                null
        );

        Set<ConstraintViolation<DcqlClaimDto>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void whenIdContainsInvalidCharacters_thenValidationFails() {
        DcqlClaimDto dto = new DcqlClaimDto(
                "claim 1",
                List.of("credentialSubject", "given_name"),
                null
        );

        Set<ConstraintViolation<DcqlClaimDto>> violations = validator.validate(dto);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("id");
                    assertThat(violation.getMessage())
                            .isEqualTo("id must contain only alphanumeric, underscore, or hyphen characters");
                });
    }

    @Test
    void whenPathIsEmpty_thenValidationFails() {
        DcqlClaimDto dto = new DcqlClaimDto(
                "claim_1",
                List.of(),
                null
        );

        Set<ConstraintViolation<DcqlClaimDto>> violations = validator.validate(dto);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("path");
                    assertThat(violation.getMessage()).isEqualTo("path must not be empty");
                });
    }

    @Test
    void whenPathContainsUnsupportedElement_thenValidationFails() {
        DcqlClaimDto dto = new DcqlClaimDto(
                "claim_1",
                List.of(Boolean.TRUE),
                null
        );

        Set<ConstraintViolation<DcqlClaimDto>> violations = validator.validate(dto);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("path");
                    assertThat(violation.getMessage())
                            .isEqualTo("Invalid Dcql path value; only supporting String, Number and null");
                });
    }

    @Test
    void whenValuesIsEmpty_thenValidationFails() {
        DcqlClaimDto dto = new DcqlClaimDto(
                "claim_1",
                List.of("credentialSubject", "given_name"),
                List.of()
        );

        Set<ConstraintViolation<DcqlClaimDto>> violations = validator.validate(dto);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("values");
                    assertThat(violation.getMessage())
                            .isEqualTo("values must contain at least one element if present");
                });
    }
}


