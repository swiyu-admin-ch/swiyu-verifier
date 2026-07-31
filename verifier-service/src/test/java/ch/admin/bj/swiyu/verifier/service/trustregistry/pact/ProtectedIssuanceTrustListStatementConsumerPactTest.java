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
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.PROVIDER;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.VCT_ELFA;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.VCT_MDL;
import static ch.admin.bj.swiyu.verifier.service.trustregistry.pact.TrustRegistryConsumerPactSupport.buildApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PactConsumerTest
@PactTestFor(providerName = PROVIDER, pactVersion = PactSpecVersion.V4)
class ProtectedIssuanceTrustListStatementConsumerPactTest {

    private static final String PATH = "/api/v2/protected-issuance-trust-list";
    private static final String PI_TLS_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InN3aXl1LXByb3RlY3RlZC1pc3N1YW5jZS10cnVzdC1saXN0K2p3dCJ9."
                    + "eyJleHAiOjQxMDI0NDQ4MDAsInZjdF92YWx1ZXMiOlsiaHR0cHM6Ly9leGFtcGxlLmNoL3ZjdC9lbGZhIiwiaHR0cHM6Ly9leGFtcGxlLmNoL3ZjdC9tZGwiXX0."
                    + "cGFjdC10ZXN0LXNpZ25hdHVyZQ";

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact activeProtectedIssuanceTrustListStatement(final PactDslWithProvider builder) {
        return builder
                .given("an active Protected Issuance Trust List Statement is registered for the ecosystem",
                        Map.of("vctValues", List.of(VCT_ELFA, VCT_MDL)))
                .uponReceiving("GET the active Protected Issuance Trust List Statement")
                .method("GET")
                .path(PATH)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(200)
                .matchHeader("Content-Type", "^text/plain(?:;\\s*charset=[^;]+)?$", "text/plain")
                .body(PactDslRootValue.stringMatcher(COMPACT_JWT_REGEX, PI_TLS_JWT))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact missingProtectedIssuanceTrustListStatement(final PactDslWithProvider builder) {
        return builder
                .given("no active Protected Issuance Trust List Statement is registered for the ecosystem")
                .uponReceiving("GET the active Protected Issuance Trust List Statement when none is registered")
                .method("GET")
                .path(PATH)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(404)
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact unavailableProtectedIssuanceTrustListStatement(final PactDslWithProvider builder) {
        return builder
                .given("Protected Issuance Trust List Statement retrieval is unavailable")
                .uponReceiving("GET the Protected Issuance Trust List Statement while retrieval is unavailable")
                .method("GET")
                .path(PATH)
                .matchHeader("Accept", "\\*/\\*", "*/*")
                .willRespondWith()
                .status(500)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "activeProtectedIssuanceTrustListStatement")
    void shouldReturnActiveProtectedIssuanceTrustListStatement(final MockServer mockServer) {
        assertThat(buildApi(mockServer).getActivePiTLS().block())
                .isEqualTo(PI_TLS_JWT);
    }

    @Test
    @PactTestFor(pactMethod = "missingProtectedIssuanceTrustListStatement")
    void shouldPropagateNotFoundWhenProtectedIssuanceTrustListStatementIsMissing(final MockServer mockServer) {
        assertThatThrownBy(() -> buildApi(mockServer).getActivePiTLS().block())
                .isInstanceOf(WebClientResponseException.NotFound.class);
    }

    @Test
    @PactTestFor(pactMethod = "unavailableProtectedIssuanceTrustListStatement")
    void shouldPropagateInternalServerErrorWhenProtectedIssuanceTrustListStatementRetrievalIsUnavailable(
            final MockServer mockServer) {
        assertThatThrownBy(() -> buildApi(mockServer).getActivePiTLS().block())
                .isInstanceOf(WebClientResponseException.InternalServerError.class);
    }
}
