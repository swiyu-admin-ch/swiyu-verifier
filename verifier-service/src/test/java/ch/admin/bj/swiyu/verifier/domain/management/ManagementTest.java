package ch.admin.bj.swiyu.verifier.domain.management;

import ch.admin.bj.swiyu.verifier.domain.VerificationResultData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementTest {

    /**
     * Test case where a request nonce is generated for a management entity.
     * Expected result: the nonce is a canonical UUID version 4 using the IETF variant.
     */
    @Test
    public void testRequestNonce_isUuidVersion4() {
        Management management = Management.builder().build();

        UUID requestNonce = UUID.fromString(management.getRequestNonce());

        assertThat(requestNonce.toString()).isEqualTo(management.getRequestNonce());
        assertThat(requestNonce.version()).isEqualTo(4);
        assertThat(requestNonce.variant()).isEqualTo(2);
    }

    /**
     * Test case where two management entities are created.
     * Expected result: each entity receives a different request nonce.
     */
    @Test
    public void testRequestNonce_isFreshForEveryManagement() {
        Management firstManagement = Management.builder().build();
        Management secondManagement = Management.builder().build();

        assertThat(firstManagement.getRequestNonce()).isNotEqualTo(secondManagement.getRequestNonce());
    }

    /**
     * Test case where a request nonce is created with a prefix.
     * Expected result: the nonce is the prefix followed by '#' and a random UUID version 4.
     */
    @Test
    public void testCreateRequestNonce_withPrefix() {
        String requestNonce = Management.createRequestNonce("my-prefix");

        assertThat(requestNonce).startsWith("my-prefix#");
        UUID randomPart = UUID.fromString(requestNonce.substring("my-prefix#".length()));
        assertThat(randomPart.version()).isEqualTo(4);
    }

    /**
     * Test case where a request nonce is created without a prefix (null or blank).
     * Expected result: the nonce is only a random UUID version 4 without separator.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    public void testCreateRequestNonce_withoutPrefix(String noncePrefix) {
        String requestNonce = Management.createRequestNonce(noncePrefix);

        assertThat(requestNonce).doesNotContain("#");
        assertThat(UUID.fromString(requestNonce).toString()).isEqualTo(requestNonce);
    }

    /**
     * Test case where two request nonces are created with the same prefix.
     * Expected result: the random part differs.
     */
    @Test
    public void testCreateRequestNonce_withPrefix_isFresh() {
        assertThat(Management.createRequestNonce("prefix")).isNotEqualTo(Management.createRequestNonce("prefix"));
    }

    /**
     * Test case where both expected and provided OAuth states are blank.
     * Expected result: true
     */
    @Test
    public void testMatchesOauthState_bothBlank() {
        Management management = Management.builder().oauthState("").build();
        String state = "";
        boolean result = management.matchesOauthState(state);
        assertThat(result).as("Both states should be considered matching when blank").isTrue();
    }

    /**
     * Test case where expected OAuth state is blank but provided state is not.
     * Expected result: false
     * This would inidcate that we received a response intended for another verifier!
     */
    @Test
    public void testMatchesOauthState_expectedBlank_providedNotBlank() {
        Management management = Management.builder().oauthState("").build();
        String state = "someState";
        boolean result = management.matchesOauthState(state);
        assertThat(result).as("Expected state is blank but provided state is not").isFalse();
    }

    /**
     * Test case where both expected and provided OAuth states are set and match.
     * Expected result: true
     */
    @Test
    public void testMatchesOauthState_bothNotBlank_andMatch() {
        Management management = Management.builder().oauthState("expectedState").build();
        String state = management.getOauthState();
        boolean result = management.matchesOauthState(state);
        assertThat(result).as("Both states should be considered matching when equal").isTrue();
    }

    /**
     * Test case where both expected and provided OAuth states are set and do not match.
     * Expected result: false
     */
    @Test
    public void testMatchesOauthState_bothNotBlank_andNotMatch() {
        Management management = Management.builder().oauthState("expectedState").build();
        String state = "differentState";
        boolean result = management.matchesOauthState(state);
        assertThat(result).as("Expected state and provided state should not match").isFalse();
    }

    /**
     * Test case where redirect URI already contains percent-encoded query parameter content.
     * Expected result: existing query parameter encoding is preserved and only response_code is appended.
     */
    @Test
    public void testVerificationDone_withPreEncodedRedirectUri_preservesEncoding() {
        Management management = Management.builder()
                .state(VerificationStatus.IN_PROGRESS)
                .redirectURI(URI.create("https://wallet.example/callback?session_nonce=sessionNonce%23details"))
                .build();

        management.verificationDone(VerificationResultData.builder()
                .verifiedResponsesJsonString("{}")
                .evaluations(Map.of())
                .vpTokens(Map.of())
                .build());

        String uri = management.getRedirectURI().toASCIIString();
        assertThat(uri).contains("session_nonce=sessionNonce%23details");
        assertThat(uri).doesNotContain("%2523");
        assertThat(uri).contains("response_code=");
    }
}
