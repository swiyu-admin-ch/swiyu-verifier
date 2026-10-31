package ch.admin.bj.swiyu.verifier.domain;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CredentialEvaluation} is persisted as JSON in the "management" table (ZDD). Ensures the audit fields added
 * to {@link StatusVerificationResult} stay compatible in both directions.
 */
class CredentialEvaluationJsonTest {

    private static final String STATUS_LIST_TOKEN = "eyJ0eXAiOiJzdGF0dXNsaXN0K2p3dCJ9.eyJzdWIiOiJodHRwczovL2V4YW1wbGUuY29tL3N0YXR1cy8xIn0.c2ln";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsEvaluationPersistedBeforeStatusListTokenWasAdded() {
        var json = """
                {"credential_status":{"valid":true,"status":0},"trust_markers":{"is_trusted":true}}""";

        var evaluation = objectMapper.readValue(json, CredentialEvaluation.class);

        assertThat(evaluation.credentialStatus().valid()).isTrue();
        assertThat(evaluation.credentialStatus().status()).isZero();
        assertThat(evaluation.credentialStatus().statusListToken()).isNull();
    }

    @Test
    void roundTripsStatusListToken() {
        var evaluation = CredentialEvaluation.builder()
                .credentialStatus(StatusVerificationResult.builder()
                        .valid(false)
                        .status(1)
                        .statusListToken(STATUS_LIST_TOKEN)
                        .build())
                .build();

        var json = objectMapper.writeValueAsString(evaluation);
        var read = objectMapper.readValue(json, CredentialEvaluation.class);

        assertThat(json).contains("\"status_list_token\":\"" + STATUS_LIST_TOKEN + "\"");
        assertThat(read).isEqualTo(evaluation);
    }

    @Test
    void omitsStatusListTokenWhenNotSet() {
        var evaluation = CredentialEvaluation.builder()
                .credentialStatus(StatusVerificationResult.builder().valid(true).status(0).build())
                .build();

        var json = objectMapper.writeValueAsString(evaluation);

        assertThat(json).doesNotContain("status_list_token");
    }
}
