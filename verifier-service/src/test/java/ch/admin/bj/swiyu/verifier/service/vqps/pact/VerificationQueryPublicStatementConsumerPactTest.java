package ch.admin.bj.swiyu.verifier.service.vqps.pact;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.LambdaDsl;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import ch.admin.bj.swiyu.verifier.domain.vqps.Vqps;
import ch.admin.bj.swiyu.verifier.dto.management.VerificationPurposeDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.service.vqps.pact.BusinessTrustConsumerPactSupport.COMPACT_JWT_REGEX;
import static ch.admin.bj.swiyu.verifier.service.vqps.pact.BusinessTrustConsumerPactSupport.CONSUMER;
import static ch.admin.bj.swiyu.verifier.service.vqps.pact.BusinessTrustConsumerPactSupport.PROVIDER;
import static ch.admin.bj.swiyu.verifier.service.vqps.pact.BusinessTrustConsumerPactSupport.TEST_ACCESS_TOKEN;
import static ch.admin.bj.swiyu.verifier.service.vqps.pact.BusinessTrustConsumerPactSupport.VERIFIER_DID;
import static ch.admin.bj.swiyu.verifier.service.vqps.pact.BusinessTrustConsumerPactSupport.buildFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@PactConsumerTest
@PactTestFor(providerName = PROVIDER, pactVersion = PactSpecVersion.V4)
class VerificationQueryPublicStatementConsumerPactTest {

    private static final String PATH = "/api/v1/trust/vqps-submissions";
    private static final String SCOPE = "com.example.age_verification";
    private static final String PURPOSE_NAME = "Age verification";
    private static final String PURPOSE_DESCRIPTION = "Confirm the holder is old enough.";
    private static final String CREDENTIAL_ID = "age_credential";
    private static final String CREDENTIAL_FORMAT = "dc+sd-jwt";
    private static final String VCT = "https://example.ch/vct/elfa";
    private static final String CLAIM = "birth_date";
    private static final long PUBLICATION_EXPIRES_AT = 4_102_444_800L; // 2100-01-01 00:00 UTC
    private static final long VERIFICATION_EXPIRES_AT = 4_102_441_200L; // 2099-12-31 23:00 UTC

    private static final Map<String, Object> DCQL_QUERY = Map.of(
            "credentials", List.of(Map.of(
                    "id", CREDENTIAL_ID,
                    "format", CREDENTIAL_FORMAT,
                    "meta", Map.of("vct_values", List.of(VCT)),
                    "claims", List.of(Map.of("path", List.of(CLAIM))))));

    private static final VerificationPurposeDto PURPOSE = VerificationPurposeDto.builder()
            .scope(SCOPE)
            .purposeName(Map.of("default", PURPOSE_NAME))
            .purposeDescription(Map.of("default", PURPOSE_DESCRIPTION))
            .build();

    private static final String VQPS_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InN3aXl1LXZlcmlmaWNhdGlvbi1xdWVyeS1wdWJsaWMtc3RhdGVtZW50K2p3dCJ9."
                    + "eyJ0eXAiOiJzd2l5dS12ZXJpZmljYXRpb24tcXVlcnktcHVibGljLXN0YXRlbWVudCIsInN1YiI6ImRpZDp3ZWJ2aDpRbVdteW9Nb2N0ZmJBYWlFczVyOWdmM3ZRZnZUOW1aUWgxa1N0S2E4QlJjTVQ1OmlkZW50aWZpZXIuYWRtaW4uY2g6YXBpOnYxOmRpZCIsInNjb3BlIjoiY29tLmV4YW1wbGUuYWdlX3ZlcmlmaWNhdGlvbiIsInB1cnBvc2VfbmFtZSI6IkFnZSB2ZXJpZmljYXRpb24iLCJwdXJwb3NlX2Rlc2NyaXB0aW9uIjoiQ29uZmlybSB0aGUgaG9sZGVyIGlzIG9sZCBlbm91Z2guIiwicmVxdWVzdCI6eyJ0eXBlIjoiRENRTCIsInNjb3BlIjoiY29tLmV4YW1wbGUuYWdlX3ZlcmlmaWNhdGlvbiIsInF1ZXJ5Ijp7ImNyZWRlbnRpYWxzIjpbeyJpZCI6ImFnZV9jcmVkZW50aWFsIiwiZm9ybWF0IjoiZGMrc2Qtand0IiwibWV0YSI6eyJ2Y3RfdmFsdWVzIjpbImh0dHBzOi8vZXhhbXBsZS5jaC92Y3QvZWxmYSJdfSwiY2xhaW1zIjpbeyJwYXRoIjpbImJpcnRoX2RhdGUiXX1dfV19fSwiZXhwIjo0MTAyNDQ0ODAwfQ."
                    + "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ";

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact publishVerificationQueryPublicStatement(final PactDslWithProvider builder) {
        return builder
                .given("a Verification Query Public Statement can be published for a verifier",
                        Map.of(
                                "verifierIdentifier", VERIFIER_DID,
                                "scope", SCOPE,
                                "purposeName", PURPOSE_NAME,
                                "purposeDescription", PURPOSE_DESCRIPTION,
                                "vctValues", List.of(VCT),
                                "publicationExpiresAt", PUBLICATION_EXPIRES_AT))
                .uponReceiving("POST a Verification Query Public Statement for publication")
                .method("POST")
                .path(PATH)
                .matchHeader("Accept", "^application/json$", "application/json")
                .matchHeader("Content-Type", "^application/json(?:;\\s*charset=[^;]+)?$", "application/json")
                .matchHeader("Authorization", "^Bearer .+$", "Bearer " + TEST_ACCESS_TOKEN)
                .body(submissionRequestBody())
                .willRespondWith()
                .status(201)
                .matchHeader("Content-Type", "^application/json(?:;\\s*charset=[^;]+)?$", "application/json")
                .body(successfulPublicationBody())
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact rejectVerificationQueryPublicStatement(final PactDslWithProvider builder) {
        return builder
                .given("Verification Query Public Statement publication is rejected for a verifier",
                        Map.of("verifierIdentifier", VERIFIER_DID))
                .uponReceiving("POST a Verification Query Public Statement that is rejected")
                .method("POST")
                .path(PATH)
                .matchHeader("Accept", "^application/json$", "application/json")
                .matchHeader("Content-Type", "^application/json(?:;\\s*charset=[^;]+)?$", "application/json")
                .matchHeader("Authorization", "^Bearer .+$", "Bearer " + TEST_ACCESS_TOKEN)
                .body(submissionRequestBody())
                .willRespondWith()
                .status(422)
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact unavailableVerificationQueryPublicStatementPublication(final PactDslWithProvider builder) {
        return builder
                .given("Verification Query Public Statement publication is unavailable",
                        Map.of("verifierIdentifier", VERIFIER_DID))
                .uponReceiving("POST a Verification Query Public Statement while publication is unavailable")
                .method("POST")
                .path(PATH)
                .matchHeader("Accept", "^application/json$", "application/json")
                .matchHeader("Content-Type", "^application/json(?:;\\s*charset=[^;]+)?$", "application/json")
                .matchHeader("Authorization", "^Bearer .+$", "Bearer " + TEST_ACCESS_TOKEN)
                .body(submissionRequestBody())
                .willRespondWith()
                .status(500)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "publishVerificationQueryPublicStatement")
    void shouldPublishAndPersistVerificationQueryPublicStatement(final MockServer mockServer) {
        final var fixture = buildFixture(mockServer);

        final String queryHash = fixture.service()
                .getOrRegisterVqps(PURPOSE, DCQL_QUERY, VERIFICATION_EXPIRES_AT);

        assertThat(queryHash).matches("^[0-9a-f]{64}$");
        final ArgumentCaptor<Vqps> savedVqps = ArgumentCaptor.forClass(Vqps.class);
        verify(fixture.repository()).save(savedVqps.capture());
        assertThat(savedVqps.getValue().getQueryHash()).isEqualTo(queryHash);
        assertThat(savedVqps.getValue().getScope()).isEqualTo(SCOPE);
        assertThat(savedVqps.getValue().getJwt()).isEqualTo(VQPS_JWT);
        assertThat(savedVqps.getValue().getExpiresAt()).isEqualTo(PUBLICATION_EXPIRES_AT);
    }

    @Test
    @PactTestFor(pactMethod = "rejectVerificationQueryPublicStatement")
    void shouldPropagateUnprocessableEntityWhenPublicationIsRejected(final MockServer mockServer) {
        final var fixture = buildFixture(mockServer);

        assertThatThrownBy(() -> fixture.service()
                .getOrRegisterVqps(PURPOSE, DCQL_QUERY, VERIFICATION_EXPIRES_AT))
                .isInstanceOf(WebClientResponseException.class)
                .satisfies(exception -> assertThat(((WebClientResponseException) exception).getStatusCode().value())
                        .isEqualTo(422));
        verify(fixture.repository(), never()).save(any(Vqps.class));
    }

    @Test
    @PactTestFor(pactMethod = "unavailableVerificationQueryPublicStatementPublication")
    void shouldPropagateInternalServerErrorWhenPublicationIsUnavailable(final MockServer mockServer) {
        final var fixture = buildFixture(mockServer);

        assertThatThrownBy(() -> fixture.service()
                .getOrRegisterVqps(PURPOSE, DCQL_QUERY, VERIFICATION_EXPIRES_AT))
                .isInstanceOf(WebClientResponseException.InternalServerError.class);
        verify(fixture.repository(), never()).save(any(Vqps.class));
    }

    private static DslPart submissionRequestBody() {
        return LambdaDsl.newJsonBody(body -> body
                .booleanValue("waitForPublication", true)
                .stringValue("sub", VERIFIER_DID)
                .object("purpose_name", names -> names.stringValue("default", PURPOSE_NAME))
                .object("purpose_description",
                        descriptions -> descriptions.stringValue("default", PURPOSE_DESCRIPTION))
                .stringValue("scope", SCOPE)
                .object("query", query -> query
                        .array("credentials", credentials -> credentials.object(credential -> credential
                                .stringValue("id", CREDENTIAL_ID)
                                .stringValue("format", CREDENTIAL_FORMAT)
                                .object("meta", meta -> meta
                                        .array("vct_values", values -> values.stringValue(VCT)))
                                .array("claims", claims -> claims.object(claim -> claim
                                        .array("path", path -> path.stringValue(CLAIM))))))))
                .build();
    }

    private static DslPart successfulPublicationBody() {
        return LambdaDsl.newJsonBody(body -> body
                .stringValue("status", "PUBLICATION_SUCCEEDED")
                .object("publicationResult", result -> result
                        .stringMatcher("jwt", COMPACT_JWT_REGEX, VQPS_JWT)))
                .build();
    }
}
