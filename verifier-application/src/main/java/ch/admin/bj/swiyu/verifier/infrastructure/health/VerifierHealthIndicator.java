package ch.admin.bj.swiyu.verifier.infrastructure.health;


import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Aggregates the cached results of individual health checkers into a single indicator.
 * <p>Propagates DOWN if any underlying checker is not UP and exposes each checkers details.</p>
 */
@Component
public class VerifierHealthIndicator implements HealthIndicator {

    /** Cached DID / identifier resolution checks. */
    private final SigningKeyVerificationHealthChecker signingKeyVerificationHealthChecker;

    public VerifierHealthIndicator(SigningKeyVerificationHealthChecker signingKeyVerificationHealthChecker) {
        this.signingKeyVerificationHealthChecker = signingKeyVerificationHealthChecker;
    }

    /**
     * Builds an aggregate {@link Health} from the latest cached results of the injected checkers.
     * Adds each checker\n's details under its name; overall status becomes first non-UP encountered.
     */
    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        Map<String, Health> checks = Map.of(
                "signingKeyVerification", signingKeyVerificationHealthChecker.getHealthResult()
        );

        checks.forEach((name, health) -> {
            builder.withDetail(name, health.getDetails());
            if (health.getStatus().equals(Status.DOWN)) {
                builder.status(health.getStatus());
            }
        });

        return builder.build();
    }
}
