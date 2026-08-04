package ch.admin.bj.swiyu.verifier.dto.management;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UriValidatorTest {

    private final UriValidator validator = new UriValidator();

    @Test
    void isValid_nullOrBlank_returnsTrue() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
        assertThat(validator.isValid("\t\n", null)).isTrue();
    }

    @Test
    void isValid_validUris_returnTrue() {
        assertThat(validator.isValid("https://example.com/path?query=1", null)).isTrue();
        assertThat(validator.isValid("http://localhost:8080/", null)).isTrue();
        assertThat(validator.isValid("urn:example:animal:ferret:nose", null)).isTrue();
    }

    @Test
    void isValid_invalidUris_returnFalse() {
        assertThat(validator.isValid("http://in valid", null)).isFalse();
        assertThat(validator.isValid("ht!tp://foo", null)).isFalse();
        assertThat(validator.isValid("://missing.scheme", null)).isFalse();
    }
}
