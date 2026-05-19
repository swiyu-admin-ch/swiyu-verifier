package ch.admin.bj.swiyu.verifier.infrastructure.config;

import ch.admin.bj.swiyu.core.trust.client.api.VqpsSubmissionB2BApi;
import ch.admin.bj.swiyu.core.trust.client.invoker.ApiClient;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring configuration for the TMS B2B Authoring API client (IF-014) used for
 * On-the-Fly vqPS registration (EIDOMNI-819).
 *
 * <p>Only active when {@code swiyu.trust-registry.tms-authoring-url} is configured.
 * Delegates Bearer token management to {@link VqpsBearerTokenProvider}.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.tms-authoring-url:}'.length() > 0")
public class VqpsB2BApiConfig {

    private final TrustRegistryProperties properties;
    private final WebClient webClient;
    private final VqpsBearerTokenProvider bearerTokenProvider;

    /**
     * Creates an {@link ApiClient} for the TMS B2B Authoring API, authenticated via
     * a lazily-fetched OAuth2 Bearer token injected per request.
     *
     * @return configured {@link ApiClient} pointing to {@code tms-authoring-url}
     */
    @Bean
    public ApiClient vqpsB2bApiClient() {
        var tokenInjectingWebClient = webClient.mutate()
                .filter((request, next) -> next.exchange(
                        ClientRequest.from(request)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerTokenProvider.getToken())
                                .build()))
                .build();

        var client = new ApiClient(tokenInjectingWebClient);
        client.setBasePath(properties.getTmsAuthoringUrl());
        log.info("Initializing TMS B2B Authoring API client for {}", properties.getTmsAuthoringUrl());
        return client;
    }

    /**
     * Exposes the generated {@link VqpsSubmissionB2BApi} as a Spring bean.
     *
     * @param vqpsB2bApiClient the configured B2B API client
     * @return the {@link VqpsSubmissionB2BApi} bean
     */
    @Bean
    public VqpsSubmissionB2BApi vqpsSubmissionB2BApi(ApiClient vqpsB2bApiClient) {
        return new VqpsSubmissionB2BApi(vqpsB2bApiClient);
    }
}
