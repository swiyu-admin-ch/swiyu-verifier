package ch.admin.bj.swiyu.verifier.dto.management;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

class UriValidatorTest {

    private final UriValidator validator = new UriValidator();

    @Test
    void isValid_null_returnsTrue() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void isValid_validHttpsUris_returnTrue() {
        assertThat(validator.isValid(URI.create("https://example.com/path?query=1"), null)).isTrue();
        assertThat(validator.isValid(URI.create("https://localhost:8443/"), null)).isTrue();
        assertThat(validator.isValid(URI.create("HTTPS://example.com/path?query=1"), null)).isTrue();
    }

    @Test
    void isValid_nonHttpsOrMissingHost_returnFalse() throws URISyntaxException {
        // non-https scheme
        assertThat(validator.isValid(URI.create("http://example.com"), null)).isFalse();

        // https scheme but missing host (scheme-specific part only)
        URI httpsNoHost = new URI("https", "/some/path", null);
        assertThat(validator.isValid(httpsNoHost, null)).isFalse();
        assertThat(validator.isValid(URI.create("/callback?session_nonce=12"), null)).isFalse();
    }
}
