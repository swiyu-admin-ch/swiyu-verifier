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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.COMPACT_JWT_REGEX;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.CONSUMER;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.PROVIDER;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.VERIFIER_DID;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.buildCacheService;
import static org.assertj.core.api.Assertions.assertThat;

@PactConsumerTest
@PactTestFor(providerName = PROVIDER, pactVersion = PactSpecVersion.V4)
class IdentityTrustStatementConsumerPactTest {

    private static final String PATH = "/api/v2/identity-trust-statement/"
            + URLEncoder.encode(VERIFIER_DID, StandardCharsets.UTF_8);
    private static final String ACTOR_IDENTIFIER = "actorIdentifier";

    private static final String ID_TS_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InN3aXl1LWlkZW50aXR5LXRydXN0LXN0YXRlbWVudCtqd3QifQ."
                    + "eyJqdGkiOiIxMTExMTExMS0xMTExLTQxMTEtODExMS0xMTExMTExMTExMTEiLCJpYXQiOjE3NjcyMjU2MDAsImV4cCI6NDEwMjQ0NDgwMCwic3ViIjoiZGlkOndlYnZoOlFtV215b01vY3RmYkFhaUVzNXI5Z2YzdlFmdlQ5bVpRaDFrU3RLYThCUmNNVDU6aWRlbnRpZmllci5hZG1pbi5jaDphcGk6djE6ZGlkIiwiZW50aXR5X25hbWUiOiJFeGFtcGxlIFB1YmxpYyBWZXJpZmllciIsImlzX3N0YXRlX2FjdG9yIjp0cnVlfQ."
                    + "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ";

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact activeIdentityTrustStatement(final PactDslWithProvider builder) {
        return builder
                .given("an Identity Trust Statement is active and registered for the actor",
                        Map.of(ACTOR_IDENTIFIER, VERIFIER_DID))
                .uponReceiving("GET an active Identity Trust Statement")
                .method("GET")
                .path(PATH)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(200)
                .matchHeader("Content-Type", "^text/plain(?:;\\s*charset=[^;]+)?$", "text/plain")
                .body(PactDslRootValue.stringMatcher(COMPACT_JWT_REGEX, ID_TS_JWT))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact missingIdentityTrustStatement(final PactDslWithProvider builder) {
        return builder
                .given("no Identity Trust Statement is registered for the actor",
                        Map.of(ACTOR_IDENTIFIER, VERIFIER_DID))
                .uponReceiving("GET a missing Identity Trust Statement")
                .method("GET")
                .path(PATH)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(404)
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact unavailableIdentityTrustStatement(final PactDslWithProvider builder) {
        return builder
                .given("Identity Trust Statement retrieval is unavailable",
                        Map.of(ACTOR_IDENTIFIER, VERIFIER_DID))
                .uponReceiving("GET an Identity Trust Statement while retrieval is unavailable")
                .method("GET")
                .path(PATH)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(500)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "activeIdentityTrustStatement")
    void shouldReturnActiveIdentityTrustStatement(final MockServer mockServer) {
        assertThat(buildCacheService(mockServer).getIdentityTrustStatement(VERIFIER_DID))
                .isEqualTo(ID_TS_JWT);
    }

    @Test
    @PactTestFor(pactMethod = "missingIdentityTrustStatement")
    void shouldReturnNullWhenIdentityTrustStatementIsMissing(final MockServer mockServer) {
        assertThat(buildCacheService(mockServer).getIdentityTrustStatement(VERIFIER_DID))
                .isNull();
    }

    @Test
    @PactTestFor(pactMethod = "unavailableIdentityTrustStatement")
    void shouldReturnNullWhenIdentityTrustStatementRetrievalIsUnavailable(final MockServer mockServer) {
        assertThat(buildCacheService(mockServer).getIdentityTrustStatement(VERIFIER_DID))
                .isNull();
    }
}
