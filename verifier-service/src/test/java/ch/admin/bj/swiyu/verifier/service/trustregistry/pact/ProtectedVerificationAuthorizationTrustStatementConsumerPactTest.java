package ch.admin.bj.swiyu.verifier.service.trustregistry.pact;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.matchingrules.MinTypeMatcher;
import au.com.dius.pact.core.model.matchingrules.RegexMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.COMPACT_JWT_REGEX;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.CONSUMER;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.PROTECTED_FIELD_FIRST_NAME;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.PROTECTED_FIELD_PERSONAL_ADMINISTRATIVE_NUMBER;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.PROVIDER;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.VERIFIER_DID;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.buildCacheService;
import static org.assertj.core.api.Assertions.assertThat;

@PactConsumerTest
@PactTestFor(providerName = PROVIDER, pactVersion = PactSpecVersion.V4)
class ProtectedVerificationAuthorizationTrustStatementConsumerPactTest {

    private static final String PATH = "/api/v2/protected-verification-authorization-trust-statement/";
    private static final String QUERY = "sub=" + VERIFIER_DID + "&filterActive=true";
    private static final String VERIFIER_IDENTIFIER = "verifierIdentifier";

    private static final String PVA_TS_FIRST_NAME_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InN3aXl1LXByb3RlY3RlZC12ZXJpZmljYXRpb24tYXV0aG9yaXphdGlvbi10cnVzdC1zdGF0ZW1lbnQrand0In0."
                    + "eyJqdGkiOiIyMjIyMjIyMi0yMjIyLTQyMjItODIyMi0yMjIyMjIyMjIyMjIiLCJpYXQiOjE3NjcyMjU2MDAsImV4cCI6NDEwMjQ0NDgwMCwic3ViIjoiZGlkOndlYnZoOlFtV215b01vY3RmYkFhaUVzNXI5Z2YzdlFmdlQ5bVpRaDFrU3RLYThCUmNNVDU6aWRlbnRpZmllci5hZG1pbi5jaDphcGk6djE6ZGlkIiwiYXV0aG9yaXplZF9maWVsZHMiOlsiZmlyc3RfbmFtZSJdfQ."
                    + "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ";

    private static final String PVA_TS_PERSONAL_ADMINISTRATIVE_NUMBER_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InN3aXl1LXByb3RlY3RlZC12ZXJpZmljYXRpb24tYXV0aG9yaXphdGlvbi10cnVzdC1zdGF0ZW1lbnQrand0In0."
                    + "eyJqdGkiOiIzMzMzMzMzMy0zMzMzLTQzMzMtODMzMy0zMzMzMzMzMzMzMzMiLCJpYXQiOjE3NjcyMjU2MDAsImV4cCI6NDEwMjQ0NDgwMCwic3ViIjoiZGlkOndlYnZoOlFtV215b01vY3RmYkFhaUVzNXI5Z2YzdlFmdlQ5bVpRaDFrU3RLYThCUmNNVDU6aWRlbnRpZmllci5hZG1pbi5jaDphcGk6djE6ZGlkIiwiYXV0aG9yaXplZF9maWVsZHMiOlsicGVyc29uYWxfYWRtaW5pc3RyYXRpdmVfbnVtYmVyIl19."
                    + "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ";

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact activeProtectedVerificationAuthorizationTrustStatements(final PactDslWithProvider builder) {
        return builder
                .given("active Protected Verification Authorization Trust Statements are registered for a verifier",
                        Map.of(
                                VERIFIER_IDENTIFIER, VERIFIER_DID,
                                "authorizedFields", List.of(
                                        PROTECTED_FIELD_FIRST_NAME,
                                        PROTECTED_FIELD_PERSONAL_ADMINISTRATIVE_NUMBER)))
                .uponReceiving("GET active Protected Verification Authorization Trust Statements")
                .method("GET")
                .path(PATH)
                .query(QUERY)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(200)
                .matchHeader("Content-Type", "^application/json(?:;\\s*charset=[^;]+)?$", "application/json")
                .body(nonEmptyContentBody())
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact emptyProtectedVerificationAuthorizationTrustStatements(final PactDslWithProvider builder) {
        return builder
                .given("no active Protected Verification Authorization Trust Statement is registered for a verifier",
                        Map.of(VERIFIER_IDENTIFIER, VERIFIER_DID))
                .uponReceiving("GET active Protected Verification Authorization Trust Statements when none are registered")
                .method("GET")
                .path(PATH)
                .query(QUERY)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(200)
                .matchHeader("Content-Type", "^application/json(?:;\\s*charset=[^;]+)?$", "application/json")
                .body(emptyContentBody())
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact unavailableProtectedVerificationAuthorizationTrustStatements(final PactDslWithProvider builder) {
        return builder
                .given("Protected Verification Authorization Trust Statement retrieval is unavailable",
                        Map.of(VERIFIER_IDENTIFIER, VERIFIER_DID))
                .uponReceiving(
                        "GET Protected Verification Authorization Trust Statements while retrieval is unavailable")
                .method("GET")
                .path(PATH)
                .query(QUERY)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(500)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "activeProtectedVerificationAuthorizationTrustStatements")
    void shouldReturnAllActiveProtectedVerificationAuthorizationTrustStatements(final MockServer mockServer) {
        assertThat(buildCacheService(mockServer)
                .getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID))
                .containsExactlyInAnyOrder(
                        PVA_TS_FIRST_NAME_JWT,
                        PVA_TS_PERSONAL_ADMINISTRATIVE_NUMBER_JWT);
    }

    @Test
    @PactTestFor(pactMethod = "emptyProtectedVerificationAuthorizationTrustStatements")
    void shouldReturnEmptyListWhenNoProtectedVerificationAuthorizationTrustStatementIsRegistered(
            final MockServer mockServer) {
        assertThat(buildCacheService(mockServer)
                .getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID))
                .isEmpty();
    }

    @Test
    @PactTestFor(pactMethod = "unavailableProtectedVerificationAuthorizationTrustStatements")
    void shouldReturnEmptyListWhenProtectedVerificationAuthorizationTrustStatementRetrievalIsUnavailable(
            final MockServer mockServer) {
        assertThat(buildCacheService(mockServer)
                .getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID))
                .isEmpty();
    }

    private static PactDslJsonBody nonEmptyContentBody() {
        final PactDslJsonBody body = new PactDslJsonBody();
        body.array("content")
                .stringValue(PVA_TS_FIRST_NAME_JWT)
                .stringValue(PVA_TS_PERSONAL_ADMINISTRATIVE_NUMBER_JWT)
                .closeArray();
        body.getMatchers()
                .addRule("$.content", new MinTypeMatcher(1))
                .addRule("$.content[*]", new RegexMatcher(COMPACT_JWT_REGEX));
        return body;
    }

    private static PactDslJsonBody emptyContentBody() {
        final PactDslJsonBody body = new PactDslJsonBody();
        body.array("content").closeArray();
        return body;
    }
}
