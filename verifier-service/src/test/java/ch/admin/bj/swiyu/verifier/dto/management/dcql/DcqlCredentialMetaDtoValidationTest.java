package ch.admin.bj.swiyu.verifier.dto.management.dcql;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DcqlCredentialMetaDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldPassValidation_whenAllFieldsAreNull() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(
                null,
                null,
                null
        );

        Set<ConstraintViolation<DcqlCredentialMetaDto>> violations = validator.validate(dto);

         assertTrue(violations.isEmpty());
    }

    @Test
    void shouldPassValidation_whenTypeValuesContainsElements() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(
                List.of(
                        List.of("VerifiableCredential", "UniversityDegreeCredential")
                ),
                null,
                null
        );

        Set<ConstraintViolation<DcqlCredentialMetaDto>> violations = validator.validate(dto);

         assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailValidation_whenTypeValuesIsEmpty() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(
                List.of(),
                null,
                null
        );

        Set<ConstraintViolation<DcqlCredentialMetaDto>> violations = validator.validate(dto);

        assertEquals(1, violations.size());

        ConstraintViolation<DcqlCredentialMetaDto> violation = violations.iterator().next();

        assertEquals("typeValues", violation.getPropertyPath().toString());
        assertEquals("type_values must not be empty when provided", violation.getMessage());
    }

    @Test
    void shouldPassValidation_whenVctValuesContainsElements() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(
                null,
                List.of("eu.europa.ec.eudi.pid.1"),
                null
        );

        Set<ConstraintViolation<DcqlCredentialMetaDto>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailValidation_whenVctValuesIsEmpty() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(
                null,
                List.of(),
                null
        );

        Set<ConstraintViolation<DcqlCredentialMetaDto>> violations = validator.validate(dto);

        assertEquals(1, violations.size());

        ConstraintViolation<DcqlCredentialMetaDto> violation = violations.iterator().next();

        assertEquals("vctValues", violation.getPropertyPath().toString());
        assertEquals("vct_values must not be empty when provided", violation.getMessage());
    }

    @Test
    void shouldPassValidation_whenDoctypeValueIsPresent() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(
                null,
                null,
                "org.iso.18013.5.1.mDL"
        );

        Set<ConstraintViolation<DcqlCredentialMetaDto>> violations = validator.validate(dto);

         assertTrue(violations.isEmpty());
    }

    @Test
    void shouldPassValidation_whenAllFieldsAreValid() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(
                List.of(List.of("VerifiableCredential")),
                List.of("eu.europa.ec.eudi.pid.1"),
                "org.iso.18013.5.1.mDL"
        );

        Set<ConstraintViolation<DcqlCredentialMetaDto>> violations = validator.validate(dto);

         assertTrue(violations.isEmpty());
    }
}
