package ch.admin.bj.swiyu.verifier.dto.management;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class SessionNonceValidatorTest {

    private final RedirectUriValidator validator = new RedirectUriValidator();

    @Test
    void isValid_nullUri_returnsTrue() {
        assertTrue(validator.isValid(null, null));
    }

    @Test
    void isValid_queryContainsSessionNonceWithValue_returnsTrue() {
        URI uri = URI.create("https://example.com/callback?session_nonce=abc123");
        assertTrue(validator.isValid(uri, null));
    }

    @Test
    void isValid_queryMissing_returnsFalse() {
        URI uri = URI.create("https://example.com/callback");
        assertFalse(validator.isValid(uri, null));
    }

    @Test
    void isValid_queryHasParamWithoutEquals_returnsFalse() {
        URI uri = URI.create("https://example.com/callback?session_nonce");
        assertFalse(validator.isValid(uri, null));
    }

    @Test
    void isValid_multipleQueryParams_sessionNoncePresent_returnsTrue() {
        URI uri = URI.create("https://example.com/callback?a=1&session_nonce=xyz&b=2");
        assertTrue(validator.isValid(uri, null));
    }

    @Test
    void isValid_sessionNonceEmpty_returnsFalse() {
        URI uri = URI.create("https://example.com/callback?session_nonce=");
        assertFalse(validator.isValid(uri, null));
    }
}
