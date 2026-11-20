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

    private static final String SCID = "QmdPxnNc9MzGYZ6qcHuvi8YpDVYWW7mmefnCTPcKapNEL5";
    private static final String VALID_WEBVH_DID = "did:webvh:" + SCID + ":www.example.com";
    private static final String VALID_TDW_DID = "did:tdw:" + SCID + ":www.admin.ch";
    private static final String VALID_MULTI_SEGMENT_WEBVH_DID = "did:webvh:" + SCID
            + ":identifier-reg.trust-infra.swiyu.admin.ch:api:v1:did:00000000-0000-0000-0000-000000000000";
    private static final String INVALID_DID_MESSAGE =
            "must be a valid did:tdw or did:webvh DID (e.g. did:webvh:<scid>:www.example.com)";

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void whenValidate_shouldAcceptValidDto() {
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto("did", List.of(VALID_WEBVH_DID, VALID_TDW_DID));

        Set<ConstraintViolation<DcqlTrustedAuthoritiesDto>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void whenValidate_shouldAcceptDidWithMultipleDomainSegments() {
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto("did", List.of(VALID_MULTI_SEGMENT_WEBVH_DID));

        assertThat(validator.validate(dto)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = "")
    @NullSource
    void whenValidateType_shouldRejectIncorrect(String type) {
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto(type, List.of(VALID_WEBVH_DID));
        assertViolation(dto, "type", "type must not be null or empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DID", "did:webvh", "did:tdw", "key", "trust-framework", " did"})
    void whenValidateType_shouldRejectAnythingButDid(String type) {
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto(type, List.of(VALID_WEBVH_DID));
        assertViolation(dto, "type", "type must be 'did'");
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
        values.add(VALID_WEBVH_DID);
        values.add(null);
        values.add(VALID_TDW_DID);
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto("did", values);
        assertViolation(dto, "values[1].<list element>", "must not be empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "did:example:12345",
            "did:key:z6MkpGZULZQdxiEuHvb26979gp1CzfqA3MJz61sXBFhoBkpj",
            "did:web:www.example.com",
            "did:webvh",
            "did:webvh:",
            "did:webvh::www.example.com",
            "did:webvh:scid:",
            "did:webvh:scid:www.example.com/",
            "did:webvh:scid:www.example.com#assert-key-01",
            "did:webvh:scid:www.example.com?versionId=1",
            "did:webvh:scid:www ex ample.com",
            "webvh:scid:www.example.com",
            "did:WEBVH:scid:www.example.com"
    })
    void whenValidateValues_shouldRejectUnsupportedOrMalformedDids(String value) {
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto("did", List.of(value));
        assertViolation(dto, "values[0].<list element>", INVALID_DID_MESSAGE);
    }

    @Test
    void whenValidateValues_shouldRejectUnsupportedDidAmongValidDids() {
        DcqlTrustedAuthoritiesDto dto = new DcqlTrustedAuthoritiesDto("did",
                List.of(VALID_WEBVH_DID, "did:example:12345"));
        assertViolation(dto, "values[1].<list element>", INVALID_DID_MESSAGE);
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
