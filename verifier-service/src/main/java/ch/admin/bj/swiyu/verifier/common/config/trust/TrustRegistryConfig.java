package ch.admin.bj.swiyu.verifier.common.config.trust;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;

import ch.admin.bj.swiyu.core.trust.client.api.TrustProtocol20Api;
import ch.admin.bj.swiyu.core.trust.client.invoker.ApiClient;
import ch.admin.bj.swiyu.jwtvalidator.UrlRestriction;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@AllArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.api-url:}'.length() > 0")
public class TrustRegistryConfig {
    private final TrustRegistryProperties trustProperties;
    private final WebClient webClient;


        /**
     * Creates the WebClient-backed {@link ApiClient} for the Trust Registry sidechannel,
     * injecting HTTP Basic Auth credentials from configuration.
     *
     * @return configured {@link ApiClient}
     */
    @Bean
    public ApiClient trustRegistryApiClient() {
        var credentials = trustProperties.customerKey() + ":" + trustProperties.customerSecret();
        var encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        var reConfigured = webClient.mutate()
                .filter((request, next) -> next.exchange(
                        ClientRequest.from(request)
                                .header(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                                .build()))
                .build();

        var client = new ApiClient(reConfigured);
        client.setBasePath(trustProperties.apiUrl().toExternalForm());
        log.info("Initializing Trust Registry sidechannel API client for {}", trustProperties.apiUrl());
        return client;
    }

    /**
     * Exposes the generated {@link TrustProtocol20Api} as a Spring bean.
     *
     * @param trustRegistryApiClient the configured API client
     * @return the Trust Protocol 2.0 API facade
     */
    @Bean
    public TrustProtocol20Api trustProtocol20Api(ApiClient trustRegistryApiClient) {
        return new TrustProtocol20Api(trustRegistryApiClient);
    }
}
