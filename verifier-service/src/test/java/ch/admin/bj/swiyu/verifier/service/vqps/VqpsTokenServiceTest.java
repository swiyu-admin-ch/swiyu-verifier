package ch.admin.bj.swiyu.verifier.service.vqps;

import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.EcosystemApiType;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenApi;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenSet;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenSetRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VqpsTokenService}.
 *
 * <p>Covers: token acquisition strategy (DB refresh-token → bootstrap → client_credentials),
 * {@link VqpsTokenService#getAccessToken()} DB lookup, and force-refresh delegation.</p>
 *
 * <p>{@link LockAssert.TestHelper#makeAllAssertsPass(boolean)} is used to bypass the
 * distributed lock assertion in tests, together with a mock {@link LockProvider} that
 * always grants the lock to enable real execution via {@link DefaultLockingTaskExecutor}.</p>
 */
class VqpsTokenServiceTest {

    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String REFRESH_TOKEN_DB = "refresh-token-from-db";
    private static final String BOOTSTRAP_TOKEN = "bootstrap-refresh-token";
    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";

    private TrustRegistryProperties properties;
    private TokenSetRepository tokenSetRepository;
    private TokenApi tokenApi;
    private VqpsTokenService service;

    @BeforeEach
    void setUp() {
        // Let LockAssert.assertLocked() always pass in tests
        LockAssert.TestHelper.makeAllAssertsPass(true);

        properties = mock(TrustRegistryProperties.class);
        tokenSetRepository = mock(TokenSetRepository.class);
        tokenApi = mock(TokenApi.class);

        // LockProvider that always grants the lock so DefaultLockingTaskExecutor executes tasks
        LockProvider lockProvider = mock(LockProvider.class);
        when(lockProvider.lock(any())).thenReturn(
                Optional.of(() -> { /* release noop */ }));

        var lockingTaskExecutor = new DefaultLockingTaskExecutor(lockProvider);
        var lockConfiguration = new LockConfiguration(
                Instant.now(), "vqpsTokenRefresh", Duration.ofMinutes(5), Duration.ofSeconds(1));

        service = new VqpsTokenService(
                properties,
                tokenSetRepository,
                lockingTaskExecutor,
                lockConfiguration,
                tokenApi
        );
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    // -------------------------------------------------------------------------
    // getAccessToken
    // -------------------------------------------------------------------------

    @Test
    void getAccessToken_whenTokenInDb_returnsAccessToken() {
        TokenSet tokenSet = tokenSetWithAccessToken(ACCESS_TOKEN);
        when(tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING)).thenReturn(Optional.of(tokenSet));

        assertThat(service.getAccessToken()).isEqualTo(ACCESS_TOKEN);
    }

    @Test
    void getAccessToken_whenNoTokenInDb_throwsIllegalStateException() {
        when(tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING)).thenReturn(Optional.empty());

        assertThatThrownBy(service::getAccessToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No token found");
    }

    // -------------------------------------------------------------------------
    // requestNewTokenSet – Scenario 1: DB refresh token available
    // -------------------------------------------------------------------------

    @Test
    void requestNewTokenSet_withDbRefreshToken_usesRefreshTokenGrant() {
        TokenSet existing = tokenSetWithRefreshToken(REFRESH_TOKEN_DB);
        when(tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING)).thenReturn(Optional.of(existing));
        when(tokenApi.getNewToken(REFRESH_TOKEN_DB, "refresh_token"))
                .thenReturn(new TokenApi.TokenResponse(ACCESS_TOKEN, "new-refresh-token"));
        when(tokenSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestNewTokenSet();

        verify(tokenApi).getNewToken(REFRESH_TOKEN_DB, "refresh_token");
    }

    // -------------------------------------------------------------------------
    // requestNewTokenSet – Scenario 2: DB refresh fails, bootstrap token used
    // -------------------------------------------------------------------------

    @Test
    void requestNewTokenSet_whenDbRefreshFails_usesBootstrapToken() {
        when(properties.getBootstrapRefreshToken()).thenReturn(BOOTSTRAP_TOKEN);
        TokenSet existing = tokenSetWithRefreshToken(REFRESH_TOKEN_DB);
        when(tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING)).thenReturn(Optional.of(existing));

        when(tokenApi.getNewToken(REFRESH_TOKEN_DB, "refresh_token"))
                .thenThrow(new RuntimeException("DB token invalid"));
        when(tokenApi.getNewToken(BOOTSTRAP_TOKEN, "refresh_token"))
                .thenReturn(new TokenApi.TokenResponse(ACCESS_TOKEN, "bootstrap-new-refresh"));
        when(tokenSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestNewTokenSet();

        verify(tokenApi).getNewToken(BOOTSTRAP_TOKEN, "refresh_token");
    }

    // -------------------------------------------------------------------------
    // requestNewTokenSet – Scenario 3: no DB token → bootstrap token used
    // -------------------------------------------------------------------------

    @Test
    void requestNewTokenSet_withNoDbToken_usesBootstrapToken() {
        when(properties.getBootstrapRefreshToken()).thenReturn(BOOTSTRAP_TOKEN);
        when(tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING)).thenReturn(Optional.empty());
        when(tokenApi.getNewToken(BOOTSTRAP_TOKEN, "refresh_token"))
                .thenReturn(new TokenApi.TokenResponse(ACCESS_TOKEN, "new-refresh-token"));
        when(tokenSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestNewTokenSet();

        verify(tokenApi).getNewToken(BOOTSTRAP_TOKEN, "refresh_token");
    }

    // -------------------------------------------------------------------------
    // requestNewTokenSet – Scenario 4: no DB, no bootstrap → exception
    // -------------------------------------------------------------------------

    @Test
    void requestNewTokenSet_withNoDbTokenAndNoBootstrap_throwsIllegalStateException() {
        when(properties.getBootstrapRefreshToken()).thenReturn(null);
        when(tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING)).thenReturn(Optional.empty());

        assertThatThrownBy(service::requestNewTokenSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bootstrap-refresh-token");
    }

    // -------------------------------------------------------------------------
    // requestNewTokenSet – persists the new token set
    // -------------------------------------------------------------------------

    @Test
    void requestNewTokenSet_persistsNewTokenSetToDb() {
        when(properties.getBootstrapRefreshToken()).thenReturn(BOOTSTRAP_TOKEN);
        when(tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS_AUTHORING)).thenReturn(Optional.empty());
        when(tokenApi.getNewToken(BOOTSTRAP_TOKEN, "refresh_token"))
                .thenReturn(new TokenApi.TokenResponse(ACCESS_TOKEN, "new-rt"));
        when(tokenSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TokenSet result = service.requestNewTokenSet();

        verify(tokenSetRepository).save(any(TokenSet.class));
        assertThat(result.getAccessToken()).isEqualTo(ACCESS_TOKEN);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static TokenSet tokenSetWithAccessToken(String accessToken) {
        TokenSet ts = new TokenSet();
        ts.apply(EcosystemApiType.TRUST_STATEMENTS_AUTHORING, new TokenApi.TokenResponse(accessToken, null));
        return ts;
    }

    private static TokenSet tokenSetWithRefreshToken(String refreshToken) {
        TokenSet ts = new TokenSet();
        ts.apply(EcosystemApiType.TRUST_STATEMENTS_AUTHORING, new TokenApi.TokenResponse(ACCESS_TOKEN, refreshToken));
        return ts;
    }
}
