package ch.admin.bj.swiyu.verifier.dto.management.dcql;

import jakarta.validation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DcqlTrustedAuthoritiesDtoTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void whenValidate_shouldAcceptValidDto() {
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto("did", List.of("did:example:a", "did:example:b"));

        Set<ConstraintViolation<DcqlTrustedAuthoritiesDto>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = "")
    @NullSource
    void whenValidateType_shouldRejectIncorrect(String type) {
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto(type, List.of("did:example:a", "did:example:b"));
        assertViolation(dto, "type", "type must not be null or empty");
    }


    @ParameterizedTest
    @MethodSource("provideLists")
    @NullSource
    void whenValidateValues_shouldRejectIncorrect(List<String> values) {
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto("did", values);
        assertViolation(dto, "values", "values must not be null or empty");
    }

    @Test
    void whenValidate_shouldRejectNullInValues() {
        List<String> values = new ArrayList<>();
        values.add(null);
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto("did", values);
        assertViolation(dto, "values[0].<list element>", "must not be empty");
    }

    @Test
    void whenValidate_shouldRejectEmptyInValues() {
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto("did", List.of(""));
        assertViolation(dto, "values[0].<list element>", "must not be empty");
    }

    @Test
    void whenValidate_shouldRejectNullAmongValidValues() {
        List<String> values = new ArrayList<>();
        values.add("did:example:a");
        values.add(null);
        values.add("did:example:b");
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto("did", values);
        assertViolation(dto, "values[1].<list element>", "must not be empty");
    }

    private void assertViolation(DcqlTrustedAuthoritiesDto dto,
                                 String property,
                                 String message) {

        Set<ConstraintViolation<DcqlTrustedAuthoritiesDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());

        assertThat(violations)
                .anySatisfy(v -> {
                    assertThat(v.getPropertyPath().toString()).isEqualTo(property);
                    assertThat(v.getMessage()).isEqualTo(message);
                });
    }

    private static Stream<List<String>> provideLists() {
        return Stream.of(List.of());
    }
}
