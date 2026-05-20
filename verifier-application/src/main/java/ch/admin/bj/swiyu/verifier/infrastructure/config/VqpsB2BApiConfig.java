package ch.admin.bj.swiyu.verifier.infrastructure.config;

import ch.admin.bj.swiyu.core.trust.client.api.VqpsSubmissionB2BApi;
import ch.admin.bj.swiyu.core.trust.client.invoker.ApiClient;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenApi;
import ch.admin.bj.swiyu.verifier.service.vqps.VqpsTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;

/**
 * Spring configuration for the TMS B2B Authoring API client (IF-014) used for
 * On-the-Fly vqPS registration.
 *
 * <p>Only active when {@code swiyu.trust-registry.tms-authoring-url} is configured.
 * Bearer token management is delegated to {@link VqpsTokenService}, which reads the
 * current access token from the shared PostgreSQL {@code token_set} table –
 * ensuring cluster-safe token usage across multiple Kubernetes pods.</p>
 *
 * <p>The WebClient filter implements a transparent 401-retry: if the server returns
 * {@code 401 Unauthorized}, a forced token refresh is triggered via
 * {@link VqpsTokenService#forceRefreshAccessToken()} and the request is retried once
 * with the new token.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.tms-authoring-url:}'.length() > 0")
public class VqpsB2BApiConfig {

    private static final Duration LOCK_AT_MOST = Duration.ofMinutes(10);
    private static final Duration LOCK_AT_LEAST = Duration.ofSeconds(1);

    private final TrustRegistryProperties properties;
    private final WebClient webClient;
    private final VqpsTokenService vqpsTokenService;

    /**
     * Provides the shared {@link LockConfiguration} bean for the vqPS token refresh lock.
     * Injected into {@link VqpsTokenService} and {@code VqpsTokenRefreshScheduler} to
     * coordinate distributed token refresh across pods.
     *
     * @return the ShedLock configuration for vqPS token refresh
     */
    @Bean
    public LockConfiguration vqpsTokenApiLockConfiguration() {
        return new LockConfiguration(Instant.now(), "vqpsTokenRefresh", LOCK_AT_MOST, LOCK_AT_LEAST);
    }

    /**
     * Creates a {@link TokenApi} proxy backed by a plain WebClient pointing at the
     * OAuth2 token endpoint. Used by {@link VqpsTokenService} to fetch and refresh tokens.
     *
     * @return the {@link TokenApi} HTTP service proxy
     */
    @Bean
    public TokenApi vqpsTokenApi() {
        var tokenWebClient = webClient.mutate()
                .baseUrl(properties.getOauthTokenUrl().toExternalForm())
                .build();
        return HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(tokenWebClient))
                .build()
                .createClient(TokenApi.class);
    }

    /**
     * Creates an {@link ApiClient} for the TMS B2B Authoring API, authenticated via
     * a Bearer token read from the shared PostgreSQL token store on every request.
     * On {@code 401 Unauthorized} responses the token is refreshed once and the
     * request is retried transparently.
     *
     * @return configured {@link ApiClient} pointing to {@code tms-authoring-url}
     */
    @Bean
    public ApiClient vqpsB2bApiClient() {
        var tokenInjectingWebClient = webClient.mutate()
                .filter((request, next) ->
                        Mono.defer(() -> Mono.justOrEmpty(vqpsTokenService.getAccessToken()))
                                .flatMap(token -> next.exchange(withBearer(request, token)))
                                .flatMap(response -> {
                                    if (response.statusCode() == HttpStatusCode.valueOf(401)) {
                                        log.debug("vqPS token expired – retrying after forced refresh");
                                        return Mono.fromCallable(vqpsTokenService::forceRefreshAccessToken)
                                                .subscribeOn(Schedulers.boundedElastic())
                                                .flatMap(newToken -> next.exchange(withBearer(request, newToken)));
                                    }
                                    return Mono.just(response);
                                })
                )
                .build();

        var client = new ApiClient(tokenInjectingWebClient);
        client.setBasePath(properties.getTmsAuthoringUrl().toExternalForm());
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

    private static ClientRequest withBearer(ClientRequest request, String token) {
        return ClientRequest.from(request)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }
}
