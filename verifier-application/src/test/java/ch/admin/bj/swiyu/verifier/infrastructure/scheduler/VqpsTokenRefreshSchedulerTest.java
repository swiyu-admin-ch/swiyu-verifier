package ch.admin.bj.swiyu.verifier.infrastructure.scheduler;

import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.EcosystemApiType;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenApi;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenSet;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenSetRepository;
import ch.admin.bj.swiyu.verifier.service.vqps.VqpsTokenService;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VqpsTokenRefreshScheduler}.
 *
 * <p>Verifies that {@link VqpsTokenService#requestNewTokenSet()} is called exactly when
 * the token refresh interval has elapsed since the last refresh, and is skipped otherwise.</p>
 */
class VqpsTokenRefreshSchedulerTest {

    private TrustRegistryProperties properties;
    private VqpsTokenService vqpsTokenService;
    private TokenSetRepository tokenSetRepository;
    private VqpsTokenRefreshScheduler scheduler;

    @BeforeEach
    void setUp() {
        LockAssert.TestHelper.makeAllAssertsPass(true);

        properties = mock(TrustRegistryProperties.class);
        vqpsTokenService = mock(VqpsTokenService.class);
        tokenSetRepository = mock(TokenSetRepository.class);

        LockProvider lockProvider = mock(LockProvider.class);
        when(lockProvider.lock(any())).thenReturn(Optional.of(() -> {}));
        var lockingTaskExecutor = new DefaultLockingTaskExecutor(lockProvider);
        var lockConfiguration = new LockConfiguration(
                Instant.now(), "vqpsTokenRefresh", Duration.ofMinutes(5), Duration.ofSeconds(1));

        when(properties.getTokenRefreshInterval()).thenReturn(Duration.ofHours(12));
        when(vqpsTokenService.requestNewTokenSet()).thenReturn(new TokenSet());

        scheduler = new VqpsTokenRefreshScheduler(
                properties,
                vqpsTokenService,
                tokenSetRepository,
                lockingTaskExecutor,
                lockConfiguration
        );
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    /**
     * Given no token set exists in DB, a refresh must always be triggered.
     */
    @Test
    void refreshTokenIfDue_whenNoTokenSetInDb_triggersRefresh() {
        when(tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING)).thenReturn(Optional.empty());

        scheduler.refreshTokenIfDue();

        verify(vqpsTokenService).requestNewTokenSet();
    }

    /**
     * Given a token set with a last-refresh timestamp older than the configured interval,
     * the refresh must be triggered.
     */
    @Test
    void refreshTokenIfDue_whenIntervalElapsed_triggersRefresh() {
        TokenSet staleTokenSet = tokenSetWithLastRefresh(Instant.now().minus(Duration.ofHours(13)));
        when(tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING))
                .thenReturn(Optional.of(staleTokenSet));

        scheduler.refreshTokenIfDue();

        verify(vqpsTokenService).requestNewTokenSet();
    }

    /**
     * Given a token set refreshed just moments ago (well within the interval),
     * the refresh must NOT be triggered.
     */
    @Test
    void refreshTokenIfDue_whenIntervalNotYetElapsed_skipsRefresh() {
        TokenSet freshTokenSet = tokenSetWithLastRefresh(Instant.now().minus(Duration.ofMinutes(30)));
        when(tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING))
                .thenReturn(Optional.of(freshTokenSet));

        scheduler.refreshTokenIfDue();

        verify(vqpsTokenService, never()).requestNewTokenSet();
    }

    /**
     * Given a token set refreshed exactly at the boundary (interval just barely elapsed),
     * the refresh must be triggered.
     */
    @Test
    void refreshTokenIfDue_whenLastRefreshAtExactBoundary_triggersRefresh() {
        TokenSet boundaryTokenSet = tokenSetWithLastRefresh(
                Instant.now().minus(Duration.ofHours(12)).minusSeconds(1));
        when(tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING))
                .thenReturn(Optional.of(boundaryTokenSet));

        scheduler.refreshTokenIfDue();

        verify(vqpsTokenService).requestNewTokenSet();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static TokenSet tokenSetWithLastRefresh(Instant lastRefresh) {
        TokenSet ts = new TokenSet();
        ts.apply(EcosystemApiType.TRUST_STATEMENTS_AUTHORING,
                new TokenApi.TokenResponse("access-token", "refresh-token"));
        try {
            var field = TokenSet.class.getDeclaredField("lastRefresh");
            field.setAccessible(true);
            field.set(ts, lastRefresh);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set lastRefresh via reflection", e);
        }
        return ts;
    }
}
