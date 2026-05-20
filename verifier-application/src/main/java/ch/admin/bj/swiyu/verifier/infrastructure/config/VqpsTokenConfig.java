package ch.admin.bj.swiyu.verifier.infrastructure.config;

import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenApi;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Spring configuration for the vqPS OAuth2 token infrastructure.
 *
 * <p>Intentionally separated from {@link VqpsB2BApiConfig} to break the circular
 * dependency that would arise if the {@link LockConfiguration} and {@link TokenApi} beans
 * were defined in the same class that also depends on
 * {@link ch.admin.bj.swiyu.verifier.service.vqps.VqpsTokenService}.</p>
 *
 * <p>Only active when {@code swiyu.trust-registry.tms-authoring-url} is configured.</p>
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.tms-authoring-url:}'.length() > 0")
public class VqpsTokenConfig {

    private static final Duration LOCK_AT_MOST = Duration.ofMinutes(10);
    private static final Duration LOCK_AT_LEAST = Duration.ofSeconds(1);

    private final TrustRegistryProperties properties;
    private final WebClient webClient;

    /**
     * Provides the shared {@link LockConfiguration} for the vqPS token refresh lock.
     * Injected into {@link ch.admin.bj.swiyu.verifier.service.vqps.VqpsTokenService} and
     * {@link ch.admin.bj.swiyu.verifier.infrastructure.scheduler.VqpsTokenRefreshScheduler}
     * to coordinate distributed token refresh across Kubernetes pods.
     *
     * @return the ShedLock configuration for vqPS token refresh
     */
    @Bean
    public LockConfiguration vqpsTokenApiLockConfiguration() {
        return new LockConfiguration(Instant.now(), "vqpsTokenRefresh", LOCK_AT_MOST, LOCK_AT_LEAST);
    }

    /**
     * Creates a {@link TokenApi} HTTP proxy pointed at the configured OAuth2 token endpoint.
     * Used by {@link ch.admin.bj.swiyu.verifier.service.vqps.VqpsTokenService} to fetch
     * and refresh tokens without manual {@code WebClient} form-post boilerplate.
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
}


