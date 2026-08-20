package ch.admin.bj.swiyu.verifier.dto.management;

import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlCredentialDto;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlCredentialMetaDto;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlQueryDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateVerificationManagementDtoTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void validate_withValidDto_shouldSucceed() {
        CreateVerificationManagementDto dto = createValidDto();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_withRedirectURI_shouldSucceed() {
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of("did:example:12345"))
                .jwtSecuredAuthorizationRequest(Boolean.TRUE)
                .responseMode(ResponseModeTypeDto.DIRECT_POST)
                .dcqlQuery(createValidDcqlQuery())
                .redirectURI(URI.create("https://wallet.example/callback?session_nonce=test"))
                .build();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_withMissingAcceptedIssuerDidsAndTrustAnchors_shouldFail() {
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .dcqlQuery(createValidDcqlQuery())
                .build();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEmpty();
            assertThat(violation.getMessage()).isEqualTo("Either acceptedIssuerDids or trustAnchors must be set and cannot be empty.");
        });
    }

    @Test
    void validate_withEmptyAcceptedIssuerDids_shouldFail() {
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of())
                .dcqlQuery(createValidDcqlQuery())
                .build();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).singleElement().satisfies(violation ->
                assertThat(violation.getMessage()).isEqualTo("Either acceptedIssuerDids or trustAnchors must be set and cannot be empty.")
        );
    }

    @Test
    void validate_withNullElementInAcceptedIssuerDids_shouldFail() {
        List<String> acceptedIssuerDids = new ArrayList<>();
        acceptedIssuerDids.add("did:example:12345");
        acceptedIssuerDids.add(null);

        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(acceptedIssuerDids)
                .dcqlQuery(createValidDcqlQuery())
                .build();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).singleElement().satisfies(violation ->
                assertThat(violation.getMessage()).isEqualTo("Either acceptedIssuerDids or trustAnchors must be set and cannot be empty.")
        );
    }

    @Test
    void validate_withInvalidTrustAnchor_shouldCascadeViolation() {
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .trustAnchors(List.of(new TrustAnchorDto("did:example:12345", "http://trust.example")))
                .dcqlQuery(createValidDcqlQuery())
                .build();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("trustAnchors[0].trustRegistryUri");
            assertThat(violation.getMessage()).isEqualTo("Trust Registry URL must utilize https");
        });
    }

    @Test
    void validate_withNullDcqlQuery_shouldFail() {
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of("did:example:12345"))
                .dcqlQuery(null)
                .build();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("dcqlQuery");
            assertThat(violation.getMessage()).isEqualTo("must not be null");
        });
    }

    @Test
    void validate_withEmptyDcqlCredentials_shouldFail() {
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of("did:example:12345"))
                .dcqlQuery(new DcqlQueryDto(List.of(), List.of()))
                .build();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("dcqlQuery.credentials");
            assertThat(violation.getMessage()).isEqualTo("credentials must not be empty");
        });
    }

    @Test
    void validate_withInvalidVerificationPurpose_shouldCascadeViolation() {
        VerificationPurposeDto verificationPurpose = VerificationPurposeDto.builder()
                .scope("verification.scope")
                .purposeName(Map.of("default", "Verification"))
                .purposeDescription(Map.of("de-ch", "Beschreibung"))
                .build();

        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of("did:example:12345"))
                .dcqlQuery(createValidDcqlQuery())
                .verificationPurpose(verificationPurpose)
                .build();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("verificationPurpose.purposeDescription");
            assertThat(violation.getMessage()).contains("must contain exactly one 'default' key");
        });
    }

    private CreateVerificationManagementDto createValidDto() {
        VerificationPurposeDto verificationPurpose = VerificationPurposeDto.builder()
                .scope("verification.scope")
                .purposeName(Map.of("default", "Verification"))
                .purposeDescription(Map.of("default", "Verification purpose"))
                .build();

        return CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of("did:example:12345"))
                .jwtSecuredAuthorizationRequest(Boolean.FALSE)
                .responseMode(ResponseModeTypeDto.DIRECT_POST)
                .configuration_override(new ConfigurationOverrideDto(
                        "https://verifier.example",
                        "did:example:verifier",
                        "did:example:verifier#key-1",
                        "key-id-1",
                        "1234",
                        Map.of("client_name", "Verifier")
                ))
                .dcqlQuery(createValidDcqlQuery())
                .verificationPurpose(verificationPurpose)
                .redirectURI(URI.create("https://wallet.example/callback?session_nonce=test"))
                .build();
    }

    private DcqlQueryDto createValidDcqlQuery() {
        DcqlCredentialMetaDto meta = new DcqlCredentialMetaDto(
                null,
                List.of("https://credentials.example.com/identity_credential"),
                null
        );
        DcqlCredentialDto credential = new DcqlCredentialDto(
                "identity_credential_dcql",
                "dc+sd-jwt",
                null,
                meta,
                null,
                null,
                Boolean.TRUE,
                null
        );
        return new DcqlQueryDto(List.of(credential), List.of());
    }

    @Test
    void validate_withRedirectURIMissingSessionNonce_shouldFail() {
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of("did:example:12345"))
                .dcqlQuery(createValidDcqlQuery())
                .redirectURI(URI.create("https://wallet.example/callback"))
                .build();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("redirectURI");
            assertThat(violation.getMessage()).isEqualTo("must be an absolute URI and contain a session_nonce parameter");
        });
    }

    @Test
    void validate_withRedirectURIEmptySessionNonce_shouldFail() {
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of("did:example:12345"))
                .dcqlQuery(createValidDcqlQuery())
                .redirectURI(URI.create("https://wallet.example/callback?session_nonce="))
                .build();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("redirectURI");
            assertThat(violation.getMessage()).isEqualTo("must be an absolute URI and contain a session_nonce parameter");
        });
    }

    @Test
    void validate_withRelativeRedirectURI_shouldFail() {
        CreateVerificationManagementDto dto = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of("did:example:12345"))
                .dcqlQuery(createValidDcqlQuery())
                .redirectURI(URI.create("/callback?session_nonce=test"))
                .build();

        Set<ConstraintViolation<CreateVerificationManagementDto>> violations = validator.validate(dto);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("redirectURI");
            assertThat(violation.getMessage()).isEqualTo("must be an absolute URI and contain a session_nonce parameter");
        });
    }
}
