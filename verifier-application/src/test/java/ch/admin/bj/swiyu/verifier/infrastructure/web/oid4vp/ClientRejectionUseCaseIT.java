package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.VerificationManagementSeeder.FIXTURES;
import static ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the client rejection flow of the response-data endpoint and the resulting management state.
 */
public class ClientRejectionUseCaseIT extends AbstractVerificationControllerIT {

    /**
     * Verifies that an {@code access_denied} rejection returns HTTP 200 and stores the description on a FAILED verification.
     */
    @Test
    void givenAccessDeniedError_whenPostErrorResponse_thenVerificationFailed() throws Exception {
        // GIVEN
        String errorDescription = "User declined the verification request";

        // WHEN / THEN
        postVerificationErrorResponse(FIXTURES.secured(), FIXTURES.secured(), "access_denied", errorDescription)
                .andExpect(status().isOk());

        // Verify that the management entity is marked as failed due to client rejection
        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().errorDescription()).isEqualTo(errorDescription);
    }

    /**
     * Verifies that the {@code client_rejected} rejection accepts an empty or any description and still fails the verification.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "Nonono"})
    void givenClientRejectedWithAnyDescription_whenPostErrorResponse_thenVerificationFailed(String errorDescription) throws Exception {
        // WHEN / THEN
        postVerificationErrorResponse(FIXTURES.secured(), FIXTURES.secured(), "client_rejected", errorDescription)
                .andExpect(status().isOk());

        // Verify that the management entity is marked as failed due to client rejection
        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().errorDescription()).isEqualTo(errorDescription);
    }

    /**
     * Verifies that a rejection without {@code error_description} fails the verification and stores no description.
     */
    @Test
    void givenClientRejectedWithoutDescription_whenPostErrorResponse_thenVerificationFailedWithoutDescription() throws Exception {
        // WHEN / THEN
        mockMvc.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", FIXTURES.secured()))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("state", FIXTURES.secured().toString())
                        .formField("error", "client_rejected"));

        // Verify that the management entity is marked as failed due to client rejection
        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().errorDescription()).isNull();
    }

    /**
     * Verifies that rejecting an expired request yields HTTP 410 and leaves the verification PENDING.
     */
    @Test
    void givenExpiredRequest_whenPostErrorResponse_thenGoneAndStateUnchanged() throws Exception {
        // WHEN / THEN
        postVerificationErrorResponse(FIXTURES.expired(), FIXTURES.expired(), "access_denied", "User cancelled")
                .andExpect(status().isGone());

        // Verify that the management entity state remains unchanged
        var managementEntity = managementEntityRepository.findById(FIXTURES.expired()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);
    }

    /**
     * Verifies that every supported OAuth error code is accepted and marks the verification FAILED.
     */
    @ParameterizedTest
    @ValueSource(strings = {"invalid_scope", "invalid_request", "invalid_client", "vp_formats_not_supported", "invalid_presentation_definition_uri", "invalid_presentation_definition_reference", "client_rejected", "access_denied"})
    void givenSupportedErrorCode_whenPostErrorResponse_thenVerificationFailed(String errorCode) throws Exception {
        postVerificationErrorResponse(FIXTURES.secured(), FIXTURES.secured(), errorCode, "No")
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    /**
     * Verifies that an unsupported error code yields HTTP 400 and leaves the verification PENDING.
     */
    @Test
    void givenUnsupportedErrorCode_whenPostErrorResponse_thenBadRequestAndStateUnchanged() throws Exception {
        // WHEN / THEN
        postVerificationErrorResponse(FIXTURES.secured(), FIXTURES.secured(), "invalid_error_type", "Some description")
                .andExpect(status().isBadRequest());

        // Verify that the management entity remains in pending state
        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);
    }

    /**
     * Posts an OID4VP error response for the given request object and state.
     */
    private @NonNull ResultActions postVerificationErrorResponse(UUID requestObjectId, UUID state, String error, String errorDescription) throws Exception {
        return mockMvc.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", requestObjectId))
                .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                .formField("state", state.toString())
                .formField("error", error)
                .formField("error_description", errorDescription));
    }
}
