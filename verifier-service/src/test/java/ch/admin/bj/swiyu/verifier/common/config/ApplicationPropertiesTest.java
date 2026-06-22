package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ApplicationProperties} validation constraints.
 *
 * <p>Validates that all Jakarta Bean Validation annotations (@NotNull, @NotBlank, @NotEmpty)
 * are correctly enforced on the configuration properties class.</p>
 */
class ApplicationPropertiesTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validate_withValidProperties_shouldSucceed() {
        ApplicationProperties properties = createValidProperties();

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertTrue(violations.isEmpty(), "Valid properties should have no violations");
    }

    @Test
    void validate_withVerificationTTLNull_shouldFail() {
        ApplicationProperties properties = createValidProperties();
        properties.setVerificationTTL(null);

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertViolationExists(violations, "verificationTTL");
    }

    @Test
    void validate_withExternalUrlNull_shouldFail() {
        ApplicationProperties properties = createValidProperties();
        properties.setExternalUrl(null);

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertViolationExists(violations, "externalUrl");
    }

    @Test
    void validate_withClientIdNull_shouldFail() {
        ApplicationProperties properties = createValidProperties();
        properties.setClientId(null);

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertViolationExists(violations, "clientId");
    }

    @Test
    void validate_withClientIdPrefixNull_shouldSucceed() {
        ApplicationProperties properties = createValidProperties();
        properties.setClientIdPrefix(null);

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertTrue(violations.isEmpty(), "clientIdPrefix is @Nullable, null should be allowed");
    }

    @Test
    void validate_withDeeplinkSchemaNullOrEmpty_shouldFail() {
        ApplicationProperties properties = createValidProperties();
        properties.setDeeplinkSchema(null);

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertViolationExists(violations, "deeplinkSchema");
    }

    @Test
    void validate_withDeeplinkSchemaEmptyString_shouldFail() {
        ApplicationProperties properties = createValidProperties();
        properties.setDeeplinkSchema("");

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertViolationExists(violations, "deeplinkSchema");
    }

    @Test
    void validate_withSigningKeyNull_shouldFail() {
        ApplicationProperties properties = createValidProperties();
        properties.setSigningKey(null);

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertViolationExists(violations, "signingKey");
    }

    @Test
    void validate_withSigningKeyVerificationMethodNull_shouldFail() {
        ApplicationProperties properties = createValidProperties();
        properties.setSigningKeyVerificationMethod(null);

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertViolationExists(violations, "signingKeyVerificationMethod");
    }

    @Test
    void validate_withKeyManagementMethodNull_shouldFail() {
        ApplicationProperties properties = createValidProperties();
        properties.setKeyManagementMethod(null);

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertViolationExists(violations, "keyManagementMethod");
    }

    @Test
    void validate_withMaxCompressedCipherTextLengthNull_shouldFail() {
        ApplicationProperties properties = createValidProperties();
        properties.setMaxCompressedCipherTextLength(null);

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertViolationExists(violations, "maxCompressedCipherTextLength");
    }

    @Test
    void validate_withHsmPropertiesNull_shouldSucceed() {
        ApplicationProperties properties = createValidProperties();
        properties.setHsm(null);

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertTrue(violations.isEmpty(), "hsm is optional and can be null");
    }

    @Test
    void validate_withAcceptedRegistryHostsNull_shouldSucceed() {
        ApplicationProperties properties = createValidProperties();
        properties.setAcceptedRegistryHosts(null);

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertTrue(violations.isEmpty(), "acceptedRegistryHosts is optional and can be null");
    }

    @Test
    void validate_withAcceptedRegistryHostsEmptyList_shouldSucceed() {
        ApplicationProperties properties = createValidProperties();
        properties.setAcceptedRegistryHosts(List.of());

        Set<ConstraintViolation<ApplicationProperties>> violations = validator.validate(properties);

        assertTrue(violations.isEmpty(), "acceptedRegistryHosts is optional and can be empty");
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Creates a valid ApplicationProperties instance with all required fields populated.
     */
    private ApplicationProperties createValidProperties() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.setVerificationTTL(3600);
        properties.setExternalUrl("https://example.com");
        properties.setClientId("test-client");
        properties.setClientIdPrefix("prefix-");
        properties.setDeeplinkSchema("app://");
        properties.setSigningKey("test-key");
        properties.setSigningKeyVerificationMethod("test-method");
        properties.setKeyManagementMethod("test-kms");
        properties.setMaxCompressedCipherTextLength(100000);
        return properties;
    }

    /**
     * Asserts that a violation exists for the given property name.
     *
     * @param violations the set of constraint violations
     * @param propertyName the expected property name with a violation
     */
    private void assertViolationExists(Set<ConstraintViolation<ApplicationProperties>> violations, String propertyName) {
        boolean found = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(propertyName));
        assertTrue(found, "Expected violation for property '" + propertyName + "' but found violations: "
                + violations.stream().map(ConstraintViolation::getPropertyPath).toList());
    }
}

