package ch.admin.bj.swiyu.verifier.infrastructure.scheduler;

import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.EcosystemApiType;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenSet;
import ch.admin.bj.swiyu.verifier.domain.ecosystem.TokenSetRepository;
import ch.admin.bj.swiyu.verifier.service.vqps.VqpsTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Scheduled job that proactively refreshes the vqPS OAuth2 token set before it expires.
 *
 * <p>Wakes up at a fixed interval (half of {@code tokenRefreshInterval} to ensure we check
 * frequently enough) and delegates to {@link VqpsTokenService#requestNewTokenSet()} when
 * the configured refresh interval has elapsed since the last refresh. The actual token
 * fetch is lock-protected inside the service to prevent concurrent refreshes across
 * multiple Kubernetes pods.</p>
 *
 * <p>Only active when {@code swiyu.trust-registry.tms-authoring-url} is configured.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.tms-authoring-url:}'.length() > 0")
public class VqpsTokenRefreshScheduler {

    private final TrustRegistryProperties properties;
    private final VqpsTokenService vqpsTokenService;
    private final TokenSetRepository tokenSetRepository;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final LockConfiguration vqpsTokenApiLockConfiguration;

    /**
     * Periodically checks whether the token refresh interval has elapsed and triggers
     * a new token acquisition if required.
     *
     * <p>The fixed delay is set to half the configured refresh interval so the scheduler
     * wakes up and compares {@code last_refresh} from the DB against the configured
     * {@code tokenRefreshInterval}. This avoids unnecessary OAuth2 calls while still
     * ensuring timely refreshes regardless of which pod ran the last refresh.</p>
     */
    @Scheduled(fixedDelayString = "#{@trustRegistryProperties.tokenRefreshInterval.toMillis() / 2}",
            initialDelayString = "#{@trustRegistryProperties.tokenRefreshInterval.toMillis() / 2}")
    @SchedulerLock(name = "vqpsTokenSchedulerCheck", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void refreshTokenIfDue() {
        log.debug("Checking if vqPS token refresh is due");

        Optional<TokenSet> tokenSet = tokenSetRepository.findById(EcosystemApiType.TRUST_STATEMENTS);

        boolean refreshDue = tokenSet
                .map(ts -> ts.getLastRefresh()
                        .plusSeconds(properties.getTokenRefreshInterval().toSeconds())
                        .isBefore(Instant.now()))
                .orElse(true);

        if (refreshDue) {
            log.info("vqPS token refresh interval elapsed – requesting new token set");
            lockingTaskExecutor.executeWithLock(
                    (Runnable) () -> vqpsTokenService.requestNewTokenSet(),
                    vqpsTokenApiLockConfiguration);
        } else {
            log.debug("vqPS token refresh not yet due, skipping");
        }
    }
}
