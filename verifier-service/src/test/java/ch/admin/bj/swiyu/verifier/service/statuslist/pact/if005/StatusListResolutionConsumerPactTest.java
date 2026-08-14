package ch.admin.bj.swiyu.verifier.service.statuslist.pact.if005;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslRootValue;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTest;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListFetchFailedException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static ch.admin.bj.swiyu.verifier.service.statuslist.pact.if005.StatusRegistryDataConsumerPactSupport.COMPACT_JWT_REGEX;
import static ch.admin.bj.swiyu.verifier.service.statuslist.pact.if005.StatusRegistryDataConsumerPactSupport.CONSUMER;
import static ch.admin.bj.swiyu.verifier.service.statuslist.pact.if005.StatusRegistryDataConsumerPactSupport.PROVIDER;
import static ch.admin.bj.swiyu.verifier.service.statuslist.pact.if005.StatusRegistryDataConsumerPactSupport.STATUS_LIST_ID;
import static ch.admin.bj.swiyu.verifier.service.statuslist.pact.if005.StatusRegistryDataConsumerPactSupport.STATUS_LIST_JWT;
import static ch.admin.bj.swiyu.verifier.service.statuslist.pact.if005.StatusRegistryDataConsumerPactSupport.STATUS_LIST_PATH;
import static ch.admin.bj.swiyu.verifier.service.statuslist.pact.if005.StatusRegistryDataConsumerPactSupport.STATUS_LIST_URI;
import static ch.admin.bj.swiyu.verifier.service.statuslist.pact.if005.StatusRegistryDataConsumerPactSupport.buildStatusListResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PactConsumerTest
@PactTestFor(providerName = PROVIDER, pactVersion = PactSpecVersion.V4)
class StatusListResolutionConsumerPactTest {

    private static final String STATUS_LIST_ID_PARAMETER = "statusListId";
    private static final String STATUS_LIST_PATH_EXPRESSION =
            "/api/v1/statuslist/${" + STATUS_LIST_ID_PARAMETER + "}.jwt";

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact publishedStatusList(final PactDslWithProvider builder) {
        return builder
                .given("a status list is published",
                        Map.of(STATUS_LIST_ID_PARAMETER, STATUS_LIST_ID.toString()))
                .uponReceiving("GET a published status list")
                .method("GET")
                .pathFromProviderState(STATUS_LIST_PATH_EXPRESSION, STATUS_LIST_PATH)
                .willRespondWith()
                .status(200)
                .matchHeader(
                        "Content-Type",
                        "^application/statuslist\\+jwt(?:;\\s*charset=[^;]+)?$",
                        "application/statuslist+jwt")
                .body(PactDslRootValue.stringMatcher(COMPACT_JWT_REGEX, STATUS_LIST_JWT))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact missingStatusList(final PactDslWithProvider builder) {
        return builder
                .given("no status list is published",
                        Map.of(STATUS_LIST_ID_PARAMETER, STATUS_LIST_ID.toString()))
                .uponReceiving("GET a missing status list")
                .method("GET")
                .pathFromProviderState(STATUS_LIST_PATH_EXPRESSION, STATUS_LIST_PATH)
                .willRespondWith()
                .status(404)
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMER, provider = PROVIDER)
    public V4Pact unavailableStatusList(final PactDslWithProvider builder) {
        return builder
                .given("status list retrieval is unavailable",
                        Map.of(STATUS_LIST_ID_PARAMETER, STATUS_LIST_ID.toString()))
                .uponReceiving("GET a status list while retrieval is unavailable")
                .method("GET")
                .pathFromProviderState(STATUS_LIST_PATH_EXPRESSION, STATUS_LIST_PATH)
                .willRespondWith()
                .status(503)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "publishedStatusList")
    void shouldResolvePublishedStatusList(final MockServer mockServer) {
        assertThat(buildStatusListResolver(mockServer).resolveStatusList(STATUS_LIST_URI))
                .isEqualTo(STATUS_LIST_JWT);
    }

    @Test
    @PactTestFor(pactMethod = "missingStatusList")
    void shouldFailClosedWhenStatusListIsMissing(final MockServer mockServer) {
        assertThatThrownBy(() -> buildStatusListResolver(mockServer).resolveStatusList(STATUS_LIST_URI))
                .isExactlyInstanceOf(StatusListFetchFailedException.class)
                .hasMessage("Status list with uri: %s%s could not be retrieved",
                        mockServer.getUrl(), STATUS_LIST_PATH);
    }

    @Test
    @PactTestFor(pactMethod = "unavailableStatusList")
    void shouldFailClosedWhenStatusListRetrievalIsUnavailable(final MockServer mockServer) {
        assertThatThrownBy(() -> buildStatusListResolver(mockServer).resolveStatusList(STATUS_LIST_URI))
                .isExactlyInstanceOf(StatusListFetchFailedException.class)
                .hasMessage("Status list with uri: %s%s could not be retrieved",
                        mockServer.getUrl(), STATUS_LIST_PATH);
    }
}
