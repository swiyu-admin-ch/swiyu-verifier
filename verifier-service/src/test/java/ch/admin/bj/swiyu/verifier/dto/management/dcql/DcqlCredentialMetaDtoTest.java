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

class DcqlCredentialMetaDtoTest {

    private static final String VCT = "eu.europa.ec.eudi.pid.1";
    private static final String TYPE_VALUES_NOT_SUPPORTED = "The type_values field is not yet supported";
    private static final String DOCTYPE_VALUE_NOT_SUPPORTED = "The doctype_value field is not yet supported";

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void whenValidate_shouldAcceptMetaWithoutAnyValue() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(null, null, null);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void whenValidate_shouldAcceptVctValues() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(null, List.of(VCT), null);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void whenValidate_shouldRejectEmptyVctValues() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(null, List.of(), null);

        assertViolation(dto, "vctValues", "vct_values must not be empty when provided");
    }

    @Test
    void whenValidate_shouldRejectTypeValues() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(
                List.of(List.of("VerifiableCredential", "UniversityDegreeCredential")),
                List.of(VCT),
                null
        );

        assertViolation(dto, "typeValues", TYPE_VALUES_NOT_SUPPORTED);
    }

    @Test
    void whenValidate_shouldRejectEmptyTypeValues() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(List.of(), List.of(VCT), null);

        assertViolation(dto, "typeValues", TYPE_VALUES_NOT_SUPPORTED);
    }

    @Test
    void whenValidate_shouldRejectDoctypeValue() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(null, List.of(VCT), "org.iso.18013.5.1.mDL");

        assertViolation(dto, "doctypeValue", DOCTYPE_VALUE_NOT_SUPPORTED);
    }

    @Test
    void whenValidate_shouldRejectAllUnsupportedFields() {
        DcqlCredentialMetaDto dto = new DcqlCredentialMetaDto(
                List.of(List.of("VerifiableCredential")),
                List.of(VCT),
                "org.iso.18013.5.1.mDL"
        );

        Set<ConstraintViolation<DcqlCredentialMetaDto>> violations = validator.validate(dto);

        assertThat(violations).hasSize(2);
        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("typeValues");
                    assertThat(violation.getMessage()).isEqualTo(TYPE_VALUES_NOT_SUPPORTED);
                })
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("doctypeValue");
                    assertThat(violation.getMessage()).isEqualTo(DOCTYPE_VALUE_NOT_SUPPORTED);
                });
    }

    private void assertViolation(DcqlCredentialMetaDto dto,
                                 String property,
                                 String message) {

        Set<ConstraintViolation<DcqlCredentialMetaDto>> violations = validator.validate(dto);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo(property);
                    assertThat(violation.getMessage()).isEqualTo(message);
                });
    }
}
