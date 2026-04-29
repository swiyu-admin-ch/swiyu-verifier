package ch.admin.bj.swiyu.verifier.infrastructure.health;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for enabling or disabling individual health checkers.
 *
 * <p>Each checker can be independently disabled via its corresponding property.
 * When a checker is disabled, it reports {@code UP} with a {@code "disabled"} detail
 * instead of performing any actual check.</p>
 *
 * <p>All checks are enabled by default.</p>
 */
@Data
@ConfigurationProperties(prefix = "application.health")
public class HealthCheckProperties {

    /**
     * Enables or disables the signing-key verification health check.
     * Set to {@code false} if no static signing key is configured (e.g. dynamic key management).
     * Can be overridden via environment variable {@code SIGNING_KEY_VERIFICATION_ENABLED}.
     */
    private boolean signingKeyVerificationEnabled = true;

    /**
     * Enables or disables the stale-callback health check.
     * Can be overridden via environment variable {@code CALLBACK_HEALTH_ENABLED}.
     */
    private boolean callbackEnabled = true;

    /**
     * Enables or disables the status-registry accessibility health check.
     * Can be overridden via environment variable {@code STATUS_REGISTRY_HEALTH_ENABLED}.
     */
    private boolean statusRegistryEnabled = true;

    /**
     * Enables or disables the identifier-registry (DID) resolution health check.
     * Can be overridden via environment variable {@code IDENTIFIER_REGISTRY_HEALTH_ENABLED}.
     */
    private boolean identifierRegistryEnabled = true;
}

