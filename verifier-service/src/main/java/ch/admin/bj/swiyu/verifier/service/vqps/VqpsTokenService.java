package ch.admin.bj.swiyu.verifier.service.vqps;

import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.EcosystemApiType;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenApi;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenSet;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenSetRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Distributed OAuth2 token manager for the TMS B2B Authoring API (vqPS registration flow).
 *
 * <p>Solves the multi-pod / refresh-token-rotation problem by persisting the current
 * token set in the shared PostgreSQL {@code token_set} table (keyed by
 * {@link EcosystemApiType#TRUST_STATEMENTS}) and coordinating token refreshes via ShedLock
 * so that only one pod performs the actual OAuth2 grant at any time.</p>
 *
 * <p>Token acquisition strategy (in order of preference):
 * <ol>
 *   <li>Refresh via {@code refresh_token} from the database (if present and
 *       {@code enableRefreshTokenFlow} is {@code true}).</li>
 *   <li>Refresh via the static {@code bootstrapRefreshToken} from properties (if
 *       configured and {@code enableRefreshTokenFlow} is {@code true}).</li>
 *   <li>Fallback: {@code client_credentials} grant.</li>
 * </ol>
 * </p>
 *
 * <p>Only active when {@code swiyu.trust-registry.tms-authoring-url} is configured.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.tms-authoring-url:}'.length() > 0")
public class VqpsTokenService {

    private final TrustRegistryProperties properties;
    private final TokenSetRepository tokenSetRepository;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final LockConfiguration vqpsTokenApiLockConfiguration;
    private final TokenApi vqpsTokenApi;

    /**
     * Bootstraps the token set once at application startup using a distributed lock so
     * that only one pod performs the initial grant in a multi-pod deployment.
     */
    @PostConstruct
    void bootstrapTokenSetRefresh() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) () -> {
                    log.info("Bootstrapping vqPS OAuth2 token set for [{}] (application startup).",
                            EcosystemApiType.TRUST_STATEMENTS);
                    try {
                        requestNewTokenSet();
                    } catch (Exception e) {
                        log.error("Could not update vqPS OAuth2 token set during bootstrap. "
                                + "Refresh token might be already used or misconfigured.", e);
                    }
                },
                vqpsTokenApiLockConfiguration);
    }

    /**
     * Returns the current access token from the database without acquiring any lock.
     *
     * <p>This is the hot path called on every outbound TMS API request. It performs a
     * single DB read and is intentionally lock-free.</p>
     *
     * @return the stored access token string
     * @throws IllegalStateException if no token set has been initialised yet
     */
    @Transactional(readOnly = true)
    public String getAccessToken() {
        return tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS)
                .map(TokenSet::getAccessToken)
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to lookup vqPS access token. No token found under key '"
                                + EcosystemApiType.TRUST_STATEMENTS + "'."));
    }

    /**
     * Forces a token refresh under the distributed lock and returns the new access token.
     *
     * <p>Intended for use in 401-retry logic in the WebClient filter.</p>
     *
     * @return the freshly acquired access token
     */
    @Transactional
    public String forceRefreshAccessToken() {
        try {
            return lockingTaskExecutor.executeWithLock(
                    () -> requestNewTokenSet().getAccessToken(),
                    vqpsTokenApiLockConfiguration).getResult();
        } catch (Throwable e) {
            throw new IllegalStateException("forceRefreshAccessToken failed", e);
        }
    }

    /**
     * Refreshes and persists the OAuth2 token set for the TMS B2B Authoring API.
     *
     * <p><b>Locking:</b> This method asserts that the ShedLock distributed lock is held.
     * Do not call this method outside a locked context.</p>
     *
     * @return the newly obtained and persisted {@link TokenSet}
     * @throws IllegalStateException if called without the required lock
     */
    public TokenSet requestNewTokenSet() {
        LockAssert.assertLocked();

        var existing = tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS);

        TokenApi.TokenResponse tokenResponse = existing
                .flatMap(this::tryRefreshWithDbToken)
                .orElseGet(this::refreshWithBootstrapOrClientCredentials);

        TokenSet tokenSet = existing.orElseGet(TokenSet::new);
        tokenSet.apply(EcosystemApiType.TRUST_STATEMENTS, tokenResponse);

        log.info("vqPS OAuth2 token set updated successfully in DB for [{}].", EcosystemApiType.TRUST_STATEMENTS);
        return tokenSetRepository.save(tokenSet);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Optional<TokenApi.TokenResponse> tryRefreshWithDbToken(TokenSet dbTokenSet) {
        try {
            var tokenResponse = getTokenResponse(dbTokenSet.getRefreshToken());
            log.info("vqPS OAuth2 token refreshed using refresh token from DB.");
            return Optional.of(tokenResponse);
        } catch (Exception e) {
            log.error("Failed to refresh vqPS token with DB token. Falling back to bootstrap/client_credentials.", e);
            return Optional.empty();
        }
    }

    private TokenApi.TokenResponse refreshWithBootstrapOrClientCredentials() {
        String bootstrap = properties.getBootstrapRefreshToken();
        if (properties.isEnableRefreshTokenFlow() && bootstrap != null && !bootstrap.isBlank()) {
            log.warn("vqPS: no valid DB token – falling back to bootstrap refresh token.");
            return getTokenResponse(bootstrap);
        }
        log.info("vqPS: using client_credentials grant (refresh token flow disabled or no bootstrap token).");
        return getTokenResponse(null);
    }

    private TokenApi.TokenResponse getTokenResponse(String refreshToken) {
        if (properties.isEnableRefreshTokenFlow() && refreshToken != null && !refreshToken.isBlank()) {
            log.debug("Requesting vqPS token via refresh_token grant for [{}]", EcosystemApiType.TRUST_STATEMENTS);
            return vqpsTokenApi.getNewToken(
                    properties.getOauthClientId(),
                    properties.getOauthClientSecret(),
                    refreshToken,
                    "refresh_token");
        }
        log.debug("Requesting vqPS token via client_credentials grant for [{}]", EcosystemApiType.TRUST_STATEMENTS);
        return vqpsTokenApi.getNewToken(
                properties.getOauthClientId(),
                properties.getOauthClientSecret(),
                "client_credentials");
    }
}

