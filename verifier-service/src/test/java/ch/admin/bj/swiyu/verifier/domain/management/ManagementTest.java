package ch.admin.bj.swiyu.verifier.domain.management;

import org.junit.jupiter.api.Test;

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
}
