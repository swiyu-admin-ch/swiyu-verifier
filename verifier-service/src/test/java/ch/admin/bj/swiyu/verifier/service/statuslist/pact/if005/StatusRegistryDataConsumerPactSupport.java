package ch.admin.bj.swiyu.verifier.service.statuslist.pact.if005;

import au.com.dius.pact.consumer.MockServer;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolver;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class StatusRegistryDataConsumerPactSupport {

    static final String CONSUMER = "swiyu-verifier";
    static final String PROVIDER = "swiyu-status-registry";

    static final UUID STATUS_LIST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final String STATUS_REGISTRY_ORIGIN = "https://status-registry.example.ch";
    static final String STATUS_LIST_PATH = "/api/v1/statuslist/" + STATUS_LIST_ID + ".jwt";
    static final String STATUS_LIST_URI = STATUS_REGISTRY_ORIGIN + STATUS_LIST_PATH;

    static final String COMPACT_JWT_REGEX =
            "^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$";
    static final String STATUS_LIST_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0In0"
                    + ".eyJpc3MiOiJodHRwczovL3N0YXR1cy1yZWdpc3RyeS5leGFtcGxlLmNoIn0"
                    + ".c2lnbmF0dXJl";

    private StatusRegistryDataConsumerPactSupport() {
    }

    static StatusListResolver buildStatusListResolver(final MockServer mockServer) {
        final UrlRewriteProperties urlRewriteProperties = new UrlRewriteProperties();
        urlRewriteProperties.setUrlMappings(Map.of(STATUS_REGISTRY_ORIGIN, mockServer.getUrl()));

        final ApplicationProperties applicationProperties = new ApplicationProperties();
        applicationProperties.setAcceptedRegistryHosts(List.of(URI.create(mockServer.getUrl()).getHost()));

        return new StatusListResolver(
                urlRewriteProperties,
                WebClient.builder().build(),
                applicationProperties);
    }
}
