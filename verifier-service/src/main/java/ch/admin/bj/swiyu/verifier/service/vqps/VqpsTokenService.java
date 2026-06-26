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
 * {@link EcosystemApiType#TRUST_STATEMENTS_AUTHORING}) and coordinating token refreshes via ShedLock
 * so that only one pod performs the actual OAuth2 grant at any time.</p>
 *
 * <p>Token acquisition strategy (in order of preference):
 * <ol>
 *   <li>Refresh via {@code refresh_token} from the database (if present).</li>
 *   <li>Refresh via the static {@code bootstrapRefreshToken} from properties.</li>
 * </ol>
 * </p>
 *
 * <p>Only active when {@code swiyu.trust-registry.tms-authoring-url} is configured.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.tms-authoring-url:}'.length() > 0 and '${swiyu.trust-registry.oauth-token-url:}'.length() > 0")
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
                            EcosystemApiType.TRUST_STATEMENTS_AUTHORING);
                    try {
                        requestNewTokenSet();
                    } catch (Exception e) {
                        // Log only the exception type and (sanitized) message – never the stack trace
                        // or response body, which can carry refresh_token / client_secret echoes from
                        // upstream OAuth2 errors.
                        log.error("Could not update vqPS OAuth2 token set during bootstrap "
                                        + "(refresh token might be already used or misconfigured). type={} message={}",
                                e.getClass().getSimpleName(), sanitize(e.getMessage()));
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
        return tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING)
                .map(TokenSet::getAccessToken)
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to lookup vqPS access token. No token found under key '"
                                + EcosystemApiType.TRUST_STATEMENTS_AUTHORING + "'."));
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

        var existing = tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING);

        TokenApi.TokenResponse tokenResponse = existing
                .flatMap(this::tryRefreshWithDbToken)
                .orElseGet(this::refreshWithBootstrapToken);

        TokenSet tokenSet = existing.orElseGet(TokenSet::new);
        tokenSet.apply(EcosystemApiType.TRUST_STATEMENTS_AUTHORING, tokenResponse);

        log.info("vqPS OAuth2 token set updated successfully in DB for [{}].", EcosystemApiType.TRUST_STATEMENTS_AUTHORING);
        return tokenSetRepository.save(tokenSet);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Optional<TokenApi.TokenResponse> tryRefreshWithDbToken(TokenSet dbTokenSet) {
        String refreshToken = dbTokenSet.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        try {
            var tokenResponse = getTokenResponse(refreshToken);
            log.info("vqPS OAuth2 token refreshed using refresh token from DB.");
            return Optional.of(tokenResponse);
        } catch (Exception e) {
            // Log only the exception type and (sanitized) message – upstream OAuth2 error responses
            // may echo back the offending refresh_token / client_secret in their body, which must
            // never end up in the log aggregator.
            log.error("Failed to refresh vqPS token with DB token. Falling back to bootstrap/client_credentials. "
                    + "type={} message={}", e.getClass().getSimpleName(), sanitize(e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Best-effort scrubbing of exception messages before logging. Truncates long messages and
     * masks anything that looks like an OAuth2 secret echoed back by the upstream provider.
     */
    private static String sanitize(String message) {
        if (message == null) {
            return "<no message>";
        }
        String trimmed = message.length() > 256 ? message.substring(0, 256) + "…" : message;
        return trimmed
                .replaceAll("(?i)(refresh_token|access_token|client_secret|authorization)\\s*[=:\"']\\s*[^\"',\\s}]+",
                        "$1=***");
    }

    /**
     * Refreshes the token using the bootstrap refresh token from configuration.
     *
     * <p>The TMS B2B Authoring API requires a refresh token – {@code client_credentials}
     * is not supported. If no bootstrap refresh token is configured, an
     * {@link IllegalStateException} is thrown to prevent a silent no-op.</p>
     *
     * @return a new token response obtained via the bootstrap refresh token
     * @throws IllegalStateException if no bootstrap refresh token is configured
     */
    private TokenApi.TokenResponse refreshWithBootstrapToken() {
        String bootstrap = properties.getBootstrapRefreshToken();
        if (bootstrap == null || bootstrap.isBlank()) {
            throw new IllegalStateException(
                    "vqPS token refresh failed: no valid DB token and no bootstrap refresh token configured. "
                            + "Set 'swiyu.trust-registry.bootstrap-refresh-token'.");
        }
        log.warn("vqPS: no valid DB token – falling back to bootstrap refresh token.");
        return vqpsTokenApi.getNewToken(bootstrap, "refresh_token");
    }

    private TokenApi.TokenResponse getTokenResponse(String refreshToken) {
        log.debug("Requesting vqPS token via refresh_token grant for [{}]", EcosystemApiType.TRUST_STATEMENTS_AUTHORING);
        return vqpsTokenApi.getNewToken(refreshToken, "refresh_token");
    }
}

