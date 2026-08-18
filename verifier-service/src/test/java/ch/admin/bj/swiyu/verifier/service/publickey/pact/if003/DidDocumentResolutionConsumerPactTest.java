package ch.admin.bj.swiyu.verifier.service.publickey.pact.if003;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslRootValue;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

import static ch.admin.bj.swiyu.verifier.service.publickey.pact.if003.BaseRegistryConsumerPactSupport.CONSUMER;
import static ch.admin.bj.swiyu.verifier.service.publickey.pact.if003.BaseRegistryConsumerPactSupport.DID;
import static ch.admin.bj.swiyu.verifier.service.publickey.pact.if003.BaseRegistryConsumerPactSupport.DID_ID;
import static ch.admin.bj.swiyu.verifier.service.publickey.pact.if003.BaseRegistryConsumerPactSupport.DID_LOG;
import static ch.admin.bj.swiyu.verifier.service.publickey.pact.if003.BaseRegistryConsumerPactSupport.DID_LOG_PATH;
import static ch.admin.bj.swiyu.verifier.service.publickey.pact.if003.BaseRegistryConsumerPactSupport.PROVIDER;
import static ch.admin.bj.swiyu.verifier.service.publickey.pact.if003.BaseRegistryConsumerPactSupport.SCID;
import static ch.admin.bj.swiyu.verifier.service.publickey.pact.if003.BaseRegistryConsumerPactSupport.VERIFICATION_METHOD_FRAGMENT;
import static ch.admin.bj.swiyu.verifier.service.publickey.pact.if003.BaseRegistryConsumerPactSupport.VERIFICATION_METHOD_ID;
import static ch.admin.bj.swiyu.verifier.service.publickey.pact.if003.BaseRegistryConsumerPactSupport.buildDidResolverFacade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PactConsumerTest
@PactTestFor(providerName = PROVIDER, pactVersion = PactSpecVersion.V4)
class DidDocumentResolutionConsumerPactTest {

    private static final String DYNAMIC_DID_LOG_PATH = "/api/v1/did/${didId}/did.jsonl";
    private static final String DID_LOG_REGEX =
            "(?s)^\\{.*\\\"method\\\"\\s*:\\s*\\\"did:webvh:1\\.0\\\""
                    + ".*\\\"state\\\"\\s*:\\s*\\{.*\\\"id\\\"\\s*:\\s*\\\"did:webvh:[^\\\"]+\\\""
                    + ".*\\\"verificationMethod\\\"\\s*:\\s*\\[.*\\\"publicKeyJwk\\\"\\s*:\\s*\\{"
                    + ".*\\\"kid\\\"\\s*:\\s*\\\"[^\\\"]+\\\".*\\}.*\\].*\\}"
                    + ".*\\\"proof\\\"\\s*:\\s*\\[.+\\].*\\}\\s*$";

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact existingDidLog(final PactDslWithProvider builder) {
        return builder
                .given("a DID log exists", didStateParameters())
                .uponReceiving("GET an existing DID log")
                .method("GET")
                .pathFromProviderState(DYNAMIC_DID_LOG_PATH, DID_LOG_PATH)
                .willRespondWith()
                .status(200)
                .matchHeader(
                        "Content-Type",
                        "^application/jsonl\\+json(?:;\\s*charset=[^;]+)?$",
                        "application/jsonl+json")
                .body(PactDslRootValue.stringMatcher(DID_LOG_REGEX, DID_LOG))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact missingDidLog(final PactDslWithProvider builder) {
        return builder
                .given("no DID log exists", didStateParameters())
                .uponReceiving("GET a missing DID log")
                .method("GET")
                .pathFromProviderState(DYNAMIC_DID_LOG_PATH, DID_LOG_PATH)
                .willRespondWith()
                .status(404)
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact unavailableDidLog(final PactDslWithProvider builder) {
        return builder
                .given("DID log retrieval is unavailable", didStateParameters())
                .uponReceiving("GET a DID log while retrieval is unavailable")
                .method("GET")
                .pathFromProviderState(DYNAMIC_DID_LOG_PATH, DID_LOG_PATH)
                .willRespondWith()
                .status(500)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "existingDidLog")
    void shouldResolveVerificationMethodFromExistingDidLog(final MockServer mockServer) {
        assertThat(buildDidResolverFacade(mockServer).resolveKey(VERIFICATION_METHOD_ID))
                .satisfies(key -> {
                    assertThat(key.getKeyID()).isEqualTo(VERIFICATION_METHOD_FRAGMENT);
                    assertThat(key.isPrivate()).isFalse();
                });
    }

    @Test
    @PactTestFor(pactMethod = "missingDidLog")
    void shouldFailClosedWhenDidLogIsMissing(final MockServer mockServer) {
        assertResponseFailure(mockServer, 404);
    }

    @Test
    @PactTestFor(pactMethod = "unavailableDidLog")
    void shouldFailClosedWhenDidLogRetrievalIsUnavailable(final MockServer mockServer) {
        assertResponseFailure(mockServer, 500);
    }

    private static Map<String, Object> didStateParameters() {
        return Map.of(
                "didId", DID_ID,
                "did", DID,
                "scid", SCID,
                "verificationMethodId", VERIFICATION_METHOD_ID);
    }

    private static void assertResponseFailure(final MockServer mockServer, final int expectedStatus) {
        assertThatThrownBy(() -> buildDidResolverFacade(mockServer).resolveKey(VERIFICATION_METHOD_ID))
                .isInstanceOf(RestClientResponseException.class)
                .satisfies(exception -> assertThat(((RestClientResponseException) exception).getStatusCode().value())
                        .isEqualTo(expectedStatus));
    }
}
