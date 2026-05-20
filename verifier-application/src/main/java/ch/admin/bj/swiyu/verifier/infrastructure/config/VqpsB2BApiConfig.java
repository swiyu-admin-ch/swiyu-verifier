package ch.admin.bj.swiyu.verifier.infrastructure.config;

import ch.admin.bj.swiyu.core.trust.client.api.VqpsSubmissionB2BApi;
import ch.admin.bj.swiyu.core.trust.client.invoker.ApiClient;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.service.vqps.VqpsTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Set;

/**
 * Spring configuration for the TMS B2B Authoring API client (IF-014) used for
 * On-the-Fly vqPS registration.
 *
 * <p>Only active when {@code swiyu.trust-registry.tms-authoring-url} is configured.
 * Bearer token management is delegated to {@link VqpsTokenService}, which reads the
 * current access token from the shared PostgreSQL {@code token_set} table –
 * ensuring cluster-safe token usage across multiple Kubernetes pods.</p>
 *
 * <p>The WebClient filter implements a transparent 401-retry <b>for idempotent methods only</b>
 * (GET / HEAD / OPTIONS). If the server returns {@code 401 Unauthorized} on a safe request,
 * a forced token refresh is triggered via {@link VqpsTokenService#forceRefreshAccessToken()}
 * and the request is retried once with the new token. Non-idempotent requests (POST / PUT
 * / PATCH / DELETE) are <b>not</b> retried automatically because:
 * <ul>
 *   <li>WebClient body publishers are one-shot – a naïve replay may either send an empty
 *       body or duplicate the original write, depending on the body publisher implementation.</li>
 *   <li>The TMS B2B submission endpoint creates publication submissions; replaying it can
 *       result in duplicated submissions and double-billing.</li>
 * </ul>
 * Higher-level retry of non-idempotent calls must be handled at the caller, where idempotency
 * can be enforced explicitly (e.g. via a deduplication key).</p>
 *
 * <p>Infrastructure beans required by {@link VqpsTokenService} ({@code LockConfiguration}
 * and {@code TokenApi}) are defined in {@link VqpsTokenConfig} to avoid a circular
 * dependency between this class and the service.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.tms-authoring-url:}'.length() > 0")
public class VqpsB2BApiConfig {

    /**
     * HTTP methods considered safe to replay automatically on a 401 response.
     * Non-idempotent methods (POST / PUT / PATCH / DELETE) are excluded on purpose – see class JavaDoc.
     */
    private static final Set<HttpMethod> IDEMPOTENT_RETRY_METHODS =
            Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    private final TrustRegistryProperties properties;
    private final WebClient webClient;
    private final VqpsTokenService vqpsTokenService;


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
                                    if (response.statusCode() == HttpStatusCode.valueOf(401)
                                            && IDEMPOTENT_RETRY_METHODS.contains(request.method())) {
                                        log.debug("vqPS token expired on idempotent {} – retrying after forced refresh",
                                                request.method());
                                        return Mono.fromCallable(vqpsTokenService::forceRefreshAccessToken)
                                                .subscribeOn(Schedulers.boundedElastic())
                                                .flatMap(newToken -> next.exchange(withBearer(request, newToken)));
                                    }
                                    if (response.statusCode() == HttpStatusCode.valueOf(401)) {
                                        log.warn("vqPS token rejected on non-idempotent {} – not retrying automatically; "
                                                + "caller is responsible for refresh + idempotent replay", request.method());
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
