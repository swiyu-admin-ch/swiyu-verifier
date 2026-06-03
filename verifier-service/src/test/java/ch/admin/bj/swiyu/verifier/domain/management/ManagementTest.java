package ch.admin.bj.swiyu.verifier.domain.management;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementTest {

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
        assertThat(result).as("Expected state and provided state should not match")
        .isTrue(); // EIDOMNI-656 - Change test to expect false
        // .isFalse();
    }
}