package ch.admin.bj.swiyu.verifier.service.trustregistry.pact;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslRootValue;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.COMPACT_JWT_REGEX;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.CONSUMER;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.NON_COMPLIANT_ACTOR_DID;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.PROVIDER;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.buildApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PactConsumerTest
@PactTestFor(providerName = PROVIDER, pactVersion = PactSpecVersion.V4)
class NonComplianceTrustListStatementConsumerPactTest {

    private static final String PATH = "/api/v2/non-compliance-trust-list";
    private static final String NC_TLS_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InN3aXl1LW5vbi1jb21wbGlhbmNlLXRydXN0LWxpc3Qrand0In0."
                    + "eyJleHAiOjQxMDI0NDQ4MDAsIm5vbl9jb21wbGlhbnRfYWN0b3JfaWRlbnRpZmllcnMiOlsiZGlkOmV4YW1wbGU6bm9uLWNvbXBsaWFudC1hY3RvciJdfQ."
                    + "cGFjdC10ZXN0LXNpZ25hdHVyZQ";

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact activeNonComplianceTrustListStatement(final PactDslWithProvider builder) {
        return builder
                .given("an active Non-Compliance Trust List Statement is registered for the ecosystem",
                        Map.of("nonCompliantActorIdentifiers", List.of(NON_COMPLIANT_ACTOR_DID)))
                .uponReceiving("GET the active Non-Compliance Trust List Statement")
                .method("GET")
                .path(PATH)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(200)
                .matchHeader("Content-Type", "^text/plain(?:;\\s*charset=[^;]+)?$", "text/plain")
                .body(PactDslRootValue.stringMatcher(COMPACT_JWT_REGEX, NC_TLS_JWT))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact missingNonComplianceTrustListStatement(final PactDslWithProvider builder) {
        return builder
                .given("no active Non-Compliance Trust List Statement is registered for the ecosystem")
                .uponReceiving("GET the active Non-Compliance Trust List Statement when none is registered")
                .method("GET")
                .path(PATH)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(404)
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact unavailableNonComplianceTrustListStatement(final PactDslWithProvider builder) {
        return builder
                .given("Non-Compliance Trust List Statement retrieval is unavailable")
                .uponReceiving("GET the Non-Compliance Trust List Statement while retrieval is unavailable")
                .method("GET")
                .path(PATH)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(500)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "activeNonComplianceTrustListStatement")
    void shouldReturnActiveNonComplianceTrustListStatement(final MockServer mockServer) {
        assertThat(buildApi(mockServer).getActiveNcTLS().block())
                .isEqualTo(NC_TLS_JWT);
    }

    @Test
    @PactTestFor(pactMethod = "missingNonComplianceTrustListStatement")
    void shouldPropagateNotFoundWhenNonComplianceTrustListStatementIsMissing(final MockServer mockServer) {
        assertThatThrownBy(() -> buildApi(mockServer).getActiveNcTLS().block())
                .isInstanceOf(WebClientResponseException.NotFound.class);
    }

    @Test
    @PactTestFor(pactMethod = "unavailableNonComplianceTrustListStatement")
    void shouldPropagateInternalServerErrorWhenNonComplianceTrustListStatementRetrievalIsUnavailable(
            final MockServer mockServer) {
        assertThatThrownBy(() -> buildApi(mockServer).getActiveNcTLS().block())
                .isInstanceOf(WebClientResponseException.InternalServerError.class);
    }
}
