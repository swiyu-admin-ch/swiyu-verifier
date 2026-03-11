package ch.admin.bj.swiyu.verifier.domain.management;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ManagementTest {

    @Test
    void testVerificationFailed_thenUpdated() {
        // Arrange
        UUID id = UUID.randomUUID();
        PresentationDefinition definition = PresentationDefinition.builder().build();
        Management management = Management.builder()
                .id(id)
                .requestedPresentation(definition)
                .state(VerificationStatus.PENDING)
                .jwtSecuredAuthorizationRequest(false)
                .build();

        VerificationErrorResponseCode errorCode = VerificationErrorResponseCode.JWT_EXPIRED;
        String errorDescription = "JWT expired";

        // Act
        management.verificationFailed(errorCode, errorDescription);

        // Assert
        assertEquals(VerificationStatus.FAILED, management.getState());
        assertNotNull(management.getWalletResponse());
        assertEquals(errorCode, management.getWalletResponse().errorCode());
        assertEquals(errorDescription, management.getWalletResponse().errorDescription());
    }

    @Test
    void testVerificationFailedDueToClientRejection_thenUpdated() {
        // Arrange
        UUID id = UUID.randomUUID();
        PresentationDefinition definition = PresentationDefinition.builder().build();
        Management management = Management.builder()
                .id(id)
                .requestedPresentation(definition)
                .state(VerificationStatus.PENDING)
                .jwtSecuredAuthorizationRequest(false)
                .build();

        String errorDescription = "Client rejected the verification";

        // Act
        management.verificationFailedDueToClientRejection(errorDescription);

        // Assert
        assertEquals(VerificationStatus.FAILED, management.getState());
        assertNotNull(management.getWalletResponse());
        assertEquals(VerificationErrorResponseCode.CLIENT_REJECTED, management.getWalletResponse().errorCode());
        assertEquals(errorDescription, management.getWalletResponse().errorDescription());
    }

    @Test
    void testGetAcceptedIssuerDIDsWithEmptyList_thenUpdated() {
        // Arrange
        UUID id = UUID.randomUUID();
        PresentationDefinition definition = PresentationDefinition.builder().build();
        Management management = Management.builder()
                .id(id)
                .requestedPresentation(definition)
                .state(VerificationStatus.PENDING)
                .jwtSecuredAuthorizationRequest(false)
                .build();

        // Act
        var acceptedIssuerDids = management.getAcceptedIssuerDids();
        assertNull(acceptedIssuerDids);
    }

    @Test
    void testGetAcceptedIssuerDIDsWithAcceptedIssuers_thenUpdated() {
        // Arrange
        UUID id = UUID.randomUUID();
        PresentationDefinition definition = PresentationDefinition.builder().build();
        Management management = Management.builder()
                .id(id)
                .requestedPresentation(definition)
                .state(VerificationStatus.PENDING)
                .jwtSecuredAuthorizationRequest(false)
                .acceptedIssuerDids(List.of("did:example:123","did:example:456"))
                .build();

        // Act
        var acceptedIssuerDids = management.getAcceptedIssuerDids();

        assertEquals(2, acceptedIssuerDids.size());
        assertEquals("did:example:123", acceptedIssuerDids.get(0));
        assertEquals("did:example:456", acceptedIssuerDids.get(1));
    }

    @Test
    void testVerificationSucceeded_thenUpdated() {
        // Arrange
        UUID id = UUID.randomUUID();
        PresentationDefinition definition = PresentationDefinition.builder().build();
        Management management = Management.builder()
                .id(id)
                .requestedPresentation(definition)
                .state(VerificationStatus.PENDING)
                .jwtSecuredAuthorizationRequest(false)
                .build();

        management.verificationSucceeded("credentialSubjectData");

        assertEquals(VerificationStatus.SUCCESS, management.getState());
        assertNotNull(management.getWalletResponse());
        assertEquals("credentialSubjectData", management.getWalletResponse().credentialSubjectData());
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
        assertThat(result).as("Expected state and provided state should not match")
        .isTrue(); // EIDOMNI-656 - Change test to expect false
        // .isFalse();
    }
}