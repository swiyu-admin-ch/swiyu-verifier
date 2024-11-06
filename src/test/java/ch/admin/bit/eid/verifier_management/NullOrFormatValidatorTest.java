package ch.admin.bit.eid.verifier_management;

import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import ch.admin.bit.eid.verifier_management.models.validations.implementations.NullOrFormatValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NullOrFormatValidatorTest {

    private NullOrFormatValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new NullOrFormatValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @Test
    void testIsValid_NullFormat() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void testIsValid_EmptyFormat() {
        assertFalse(validator.isValid(Map.of(), context));
    }

    @Test
    void testIsValid_ValidFormat() {
        FormatAlgorithmDto dto = FormatAlgorithmDto.builder()
                .alg(List.of("alg1", "alg2"))
                .keyBindingAlg(List.of("keyBindingAlg1"))
                .build();

        Map<String, FormatAlgorithmDto> format = Map.of("vc+sd-jwt", dto);

        assertTrue(validator.isValid(format, context));
    }

    @Test
    void testIsValid_InvalidFormatKey() {
        FormatAlgorithmDto dto = FormatAlgorithmDto.builder()
                .alg(List.of("alg1", "alg2"))
                .keyBindingAlg(List.of("keyBindingAlg1"))
                .build();

        Map<String, FormatAlgorithmDto> format = Map.of("invalid-key", dto);

        assertFalse(validator.isValid(format, context));
    }

    @Test
    void testIsValid_InvalidFormatValue() {
        FormatAlgorithmDto dto = FormatAlgorithmDto.builder()
                .alg(List.of())
                .keyBindingAlg(null)
                .build();

        Map<String, FormatAlgorithmDto> format = Map.of("vc+sd-jwt", dto);

        assertFalse(validator.isValid(format, context));
    }
}
