package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.core.trust.client.api.TrustProtocol20Api;
import ch.admin.bj.swiyu.core.trust.client.invoker.ApiClient;
import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.UrlRestriction;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Spring configuration for the Trust Registry sidechannel API client and JWT validator.
 *
 * <p>Only active when {@code swiyu.trust-registry.api-url} is configured.
 * Uses HTTP Basic Auth (customer key / secret) to authenticate against the trust registry.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.api-url:}'.length() > 0")
public class TrustRegistryConfig {

    private final TrustRegistryProperties properties;
    private final ApplicationProperties applicationProperties;
    private final WebClient webClient;

    /**
     * Creates the WebClient-backed {@link ApiClient} for the Trust Registry sidechannel,
     * injecting HTTP Basic Auth credentials from configuration.
     *
     * @return configured {@link ApiClient}
     */
    @Bean
    public ApiClient trustRegistryApiClient() {
        var credentials = properties.getCustomerKey() + ":" + properties.getCustomerSecret();
        var encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        var reConfigured = webClient.mutate()
                .filter((request, next) -> next.exchange(
                        ClientRequest.from(request)
                                .header(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                                .build()))
                .build();

        var client = new ApiClient(reConfigured);
        client.setBasePath(properties.getApiUrl());
        log.info("Initializing Trust Registry sidechannel API client for {}", properties.getApiUrl());
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

    /**
     * Creates a {@link DidJwtValidator} restricted to the configured Trust Registry host.
     *
     * <p>The allowlist is derived from the {@code swiyu.trust-registry.api-url} property,
     * ensuring that trust statement JWTs are only accepted when their {@code kid} resolves
     * to the same host as the configured TMS endpoint.</p>
     *
     * @return the {@link DidJwtValidator} bean named {@code trustStatementDidJwtValidator}
     * @throws IllegalArgumentException if the configured {@code api-url} is malformed
     */
    @Bean
    public DidJwtValidator trustStatementDidJwtValidator() {
        Set<String> hosts = new HashSet<>(applicationProperties.getAcceptedRegistryHosts());
        log.info("Configuring trust statement JWT validator with allowed host: {}", hosts);
        return new DidJwtValidator(new UrlRestriction(hosts));
    }
}

