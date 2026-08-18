package ch.admin.bj.swiyu.verifier.dto.management.dcql;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

public class DcqlCredentialDtoTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void whenValidate_shouldAcceptValidDto() {
        DcqlCredentialDto dto = getValidDto();

        Set<ConstraintViolation<DcqlCredentialDto>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }

    @Test
    void whenValidate_shouldRejectEmptyId() {
        DcqlCredentialDto dto = new DcqlCredentialDto(
                "",
                "vc+sd-jwt",
                false,
                getValidMeta(),
                getValidClaims(),
                null,
                true,
                null
        );

        assertViolation(dto, "id", "id must not be empty");
    }

    @Test
    void whenValidate_shouldRejectInvalidIdCharacters() {
        DcqlCredentialDto dto = new DcqlCredentialDto(
                "invalid id!",
                "vc+sd-jwt",
                false,
                getValidMeta(),
                getValidClaims(),
                null,
                true,
                null
        );

        assertViolation(dto, "id",
                "id must contain only alphanumeric, underscore, or hyphen characters");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {""})
    void whenValidate_shouldRejectNullOrEmptyFormat(String format) {
        DcqlCredentialDto dto = new DcqlCredentialDto(
                "credential_1",
                format,
                false,
                getValidMeta(),
                getValidClaims(),
                null,
                true,
                null
        );

        assertViolation(dto, "format", "format must not be empty");
    }

    @Test
    void whenValidate_shouldRejectNullMeta() {
        DcqlCredentialDto dto = new DcqlCredentialDto(
                "credential_1",
                "vc+sd-jwt",
                false,
                null,
                getValidClaims(),
                null,
                true,
                null
        );

        assertViolation(dto, "meta", "meta is required");
    }

    @Test
    void whenValidate_shouldRejectMultipleTrue() {
        DcqlCredentialDto dto = new DcqlCredentialDto(
                "credential_1",
                "vc+sd-jwt",
                true,
                getValidMeta(),
                getValidClaims(),
                null,
                true,
                null
        );

        assertViolation(dto, "multiple",
                "'multiple' is not supported and must be false or omitted");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false})
    void whenValidate_shouldAcceptMultipleFalseOrNull(Boolean multiple) {
        DcqlCredentialDto dto = new DcqlCredentialDto(
                "credential_1",
                "vc+sd-jwt",
                multiple,
                getValidMeta(),
                getValidClaims(),
                null,
                true,
                null
        );

        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void whenValidate_shouldRejectEmptyClaimsList() {
        DcqlCredentialDto dto = new DcqlCredentialDto(
                "credential_1",
                "vc+sd-jwt",
                false,
                getValidMeta(),
                List.of(),
                null,
                true,
                null
        );

        assertViolation(dto, "claims",
                "claims must not be empty when provided");
    }

    @Test
    void whenValidate_shouldRejectClaimSetsWhenPresent() {
        DcqlCredentialDto dto = new DcqlCredentialDto(
                "credential_1",
                "vc+sd-jwt",
                false,
                getValidMeta(),
                getValidClaims(),
                List.of(List.of("claim1")),
                true,
                null
        );

        assertViolation(dto, "claimSets",
                "The claim_sets field is not yet supported");
    }

    @Test
    void whenValidate_shouldRejectTrustedAuthoritiesWhenPresent() {

        var trustedAuthorities = List.of(new DcqlTrustedAuthoritiesDto("string", List.of("test")));

        DcqlCredentialDto dto = new DcqlCredentialDto(
                "credential_1",
                "vc+sd-jwt",
                false,
                getValidMeta(),
                getValidClaims(),
                null,
                true,
                trustedAuthorities
        );

        assertViolation(dto, "trustedAuthorities",
                "The trusted_authorities field is not yet supported");
    }

    @Test
    void whenValidate_shouldRejectTrustedAuthoritiesWhenEmptyList() {

        DcqlCredentialDto dto = new DcqlCredentialDto(
                "credential_1",
                "vc+sd-jwt",
                false,
                getValidMeta(),
                getValidClaims(),
                null,
                true,
                List.of()
        );

        assertViolation(dto, "trustedAuthorities",
                "The trusted_authorities field is not yet supported");
    }


    private void assertViolation(DcqlCredentialDto dto,
                                 String property,
                                 String message) {

        Set<ConstraintViolation<DcqlCredentialDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());

        assertThat(violations)
                .anySatisfy(v -> {
                    assertThat(v.getPropertyPath().toString()).isEqualTo(property);
                    assertThat(v.getMessage()).isEqualTo(message);
                });
    }

    private DcqlCredentialDto getValidDto() {
        return new DcqlCredentialDto(
                "credential_1",
                "vc+sd-jwt",
                false,
                getValidMeta(),
                getValidClaims(),
                null,
                true,
                null
        );
    }

    private DcqlCredentialMetaDto getValidMeta() {
        return new DcqlCredentialMetaDto(List.of(List.of("vct")), null, null);
    }

    private List<DcqlClaimDto> getValidClaims() {
        return List.of(new DcqlClaimDto(null, List.of("credentialSubject", "given_name"), null));
    }
}
