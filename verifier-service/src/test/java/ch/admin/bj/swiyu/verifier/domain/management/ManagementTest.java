package ch.admin.bj.swiyu.verifier.domain.management;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
                .acceptedIssuerDids(List.of("did:example:123,did:example:456".split(",")))
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
}