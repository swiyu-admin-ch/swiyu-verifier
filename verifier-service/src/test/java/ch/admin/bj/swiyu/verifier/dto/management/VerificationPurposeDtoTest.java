package ch.admin.bj.swiyu.verifier.dto.management;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VerificationPurposeDto} validation constraints.
 *
 * <p>Tests the requirement that both {@code purpose_name} and {@code purpose_description}
 * must contain exactly one "default" key with a non-blank value, enforced by the
 * {@code @ContainsDefaultKey} custom constraint annotation.</p>
 */
class VerificationPurposeDtoTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void validate_withValidDto_shouldSucceed() {
        // Arrange
        Map<String, String> purposeName = Map.of(
                "default", "Age Verification",
                "de-ch", "Altersverifikation"
        );
        Map<String, String> purposeDescription = Map.of(
                "default", "Verify that the user is over 18 years old",
                "de-ch", "Überprüfen Sie, ob der Benutzer über 18 Jahre alt ist"
        );

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_withOnlyDefaultKey_shouldSucceed() {
        // Arrange
        Map<String, String> purposeName = Map.of("default", "Age Verification");
        Map<String, String> purposeDescription = Map.of("default", "Verify age");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_withMissingDefaultInPurposeName_shouldFail() {
        // Arrange
        Map<String, String> purposeName = Map.of("de-ch", "Altersverifikation");
        Map<String, String> purposeDescription = Map.of("default", "Verify age");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getMessage()).contains("must contain exactly one 'default' key");
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purposeName");
    }

    @Test
    void validate_withMissingDefaultInPurposeDescription_shouldFail() {
        // Arrange
        Map<String, String> purposeName = Map.of("default", "Age Verification");
        Map<String, String> purposeDescription = Map.of("de-ch", "Beschreibung");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getMessage()).contains("must contain exactly one 'default' key");
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purposeDescription");
    }

    @Test
    void validate_withNullDefaultValueInPurposeName_shouldFail() {
        // Arrange
        Map<String, String> purposeName = new HashMap<>();
        purposeName.put("default", null);
        purposeName.put("de-ch", "Altersverifikation");

        Map<String, String> purposeDescription = Map.of("default", "Verify age");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getMessage()).contains("must contain exactly one 'default' key");
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purposeName");
    }

    @Test
    void validate_withEmptyDefaultValueInPurposeName_shouldFail() {
        // Arrange
        Map<String, String> purposeName = Map.of(
                "default", "",
                "de-ch", "Altersverifikation"
        );
        Map<String, String> purposeDescription = Map.of("default", "Verify age");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getMessage()).contains("must contain exactly one 'default' key");
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purposeName");
    }

    @Test
    void validate_withBlankDefaultValueInPurposeName_shouldFail() {
        // Arrange
        Map<String, String> purposeName = Map.of(
                "default", "   ",
                "de-ch", "Altersverifikation"
        );
        Map<String, String> purposeDescription = Map.of("default", "Verify age");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getMessage()).contains("must contain exactly one 'default' key");
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purposeName");
    }

    @Test
    void validate_withNullDefaultValueInPurposeDescription_shouldFail() {
        // Arrange
        Map<String, String> purposeName = Map.of("default", "Age Verification");
        Map<String, String> purposeDescription = new HashMap<>();
        purposeDescription.put("default", null);
        purposeDescription.put("de-ch", "Beschreibung");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getMessage()).contains("must contain exactly one 'default' key");
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purposeDescription");
    }

    @Test
    void validate_withEmptyDefaultValueInPurposeDescription_shouldFail() {
        // Arrange
        Map<String, String> purposeName = Map.of("default", "Age Verification");
        Map<String, String> purposeDescription = Map.of(
                "default", "",
                "de-ch", "Beschreibung"
        );

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getMessage()).contains("must contain exactly one 'default' key");
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purposeDescription");
    }

    @Test
    void validate_withBlankDefaultValueInPurposeDescription_shouldFail() {
        // Arrange
        Map<String, String> purposeName = Map.of("default", "Age Verification");
        Map<String, String> purposeDescription = Map.of(
                "default", "\t\n  ",
                "de-ch", "Beschreibung"
        );

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getMessage()).contains("must contain exactly one 'default' key");
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purposeDescription");
    }

    @Test
    void validate_withNullScope_shouldFail() {
        // Arrange
        Map<String, String> purposeName = Map.of("default", "Age Verification");
        Map<String, String> purposeDescription = Map.of("default", "Verify age");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope(null)
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("scope");
        assertThat(violation.getMessage()).containsIgnoringCase("blank");
    }

    @Test
    void validate_withBlankScope_shouldFail() {
        // Arrange
        Map<String, String> purposeName = Map.of("default", "Age Verification");
        Map<String, String> purposeDescription = Map.of("default", "Verify age");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("   ")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("scope");
        assertThat(violation.getMessage()).containsIgnoringCase("blank");
    }

    @Test
    void validate_withNullPurposeName_shouldFail() {
        // Arrange
        Map<String, String> purposeDescription = Map.of("default", "Verify age");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(null)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purposeName");
        assertThat(violation.getMessage()).containsIgnoringCase("empty");
    }

    @Test
    void validate_withEmptyPurposeName_shouldFail() {
        // Arrange
        Map<String, String> purposeDescription = Map.of("default", "Verify age");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(Map.of())
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(2); // @NotEmpty + @ContainsDefaultKey
        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("purposeName")
        );
    }

    @Test
    void validate_withNullPurposeDescription_shouldFail() {
        // Arrange
        Map<String, String> purposeName = Map.of("default", "Age Verification");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(null)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationPurposeDto> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("purposeDescription");
        assertThat(violation.getMessage()).containsIgnoringCase("empty");
    }

    @Test
    void validate_withEmptyPurposeDescription_shouldFail() {
        // Arrange
        Map<String, String> purposeName = Map.of("default", "Age Verification");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(Map.of())
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(2); // @NotEmpty + @ContainsDefaultKey
        assertThat(violations).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("purposeDescription")
        );
    }

    @Test
    void validate_withMultipleLanguages_shouldSucceed() {
        // Arrange
        Map<String, String> purposeName = Map.of(
                "default", "Age Verification",
                "de-ch", "Altersverifikation",
                "fr-ch", "Vérification de l'âge",
                "it-ch", "Verifica dell'età"
        );
        Map<String, String> purposeDescription = Map.of(
                "default", "Verify that the user is over 18 years old",
                "de-ch", "Überprüfen Sie, ob der Benutzer über 18 Jahre alt ist",
                "fr-ch", "Vérifiez que l'utilisateur a plus de 18 ans",
                "it-ch", "Verifica che l'utente abbia più di 18 anni"
        );

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_withBothMapsInvalid_shouldReportBothViolations() {
        // Arrange
        Map<String, String> purposeName = Map.of("de-ch", "Altersverifikation");
        Map<String, String> purposeDescription = Map.of("fr-ch", "Vérification");

        VerificationPurposeDto dto = VerificationPurposeDto.builder()
                .scope("com.example.age_verification")
                .purposeName(purposeName)
                .purposeDescription(purposeDescription)
                .build();

        // Act
        Set<ConstraintViolation<VerificationPurposeDto>> violations = validator.validate(dto);

        // Assert
        assertThat(violations).hasSize(2);
        assertThat(violations).anySatisfy(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("purposeName");
            assertThat(v.getMessage()).contains("must contain exactly one 'default' key");
        });
        assertThat(violations).anySatisfy(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("purposeDescription");
            assertThat(v.getMessage()).contains("must contain exactly one 'default' key");
        });
    }
}

