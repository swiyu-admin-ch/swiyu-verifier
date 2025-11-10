package ch.admin.bj.swiyu.verifier.infrastructure.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for scheduled health checks whose last result is cached.
 * <p>Subclasses implement {@link #performCheck(Health.Builder)} to add details or mark DOWN.</p>
 * <p>The result is updated every minute (after an initial 5s delay) and served instantly to callers.</p>
 */
public abstract class CachedHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(CachedHealthChecker.class);

    private final AtomicReference<Health> healthResultRef = new AtomicReference<>(Health.unknown().build());

    /**
     * Returns the last computed health result (may be UNKNOWN before first run).
     */
    public Health getHealthResult() {
        return healthResultRef.get();
    }

    /**
     * Periodic execution updating the cached health result.
     * Marks DOWN if {@link #performCheck(Health.Builder)} throws.
     */
    @Scheduled(fixedRate = 60000, initialDelay = 5000)
    public void scheduledCheck() {
        log.info("Running health check: {}", this.getClass().getSimpleName());
        Health.Builder builder = Health.up().withDetail("lastExecution", LocalDateTime.now());
        try {
            performCheck(builder);
        } catch (Exception ex) {
            log.error("Health check failed: {}", ex.getMessage());
            builder.down(ex);
        }
        var built = builder.build();
        healthResultRef.set(built);
        log.info("Health status for {}: {}", this.getClass().getSimpleName(), built.getStatus());
    }

    /**
     * Subclass-specific logic to enrich the health builder and optionally mark it DOWN.
     * Throwing an exception automatically turns the result DOWN.
     */
    protected abstract void performCheck(Health.Builder builder) throws Exception; // NOSONAR
}
