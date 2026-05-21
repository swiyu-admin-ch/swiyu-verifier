package ch.admin.bj.swiyu.verifier.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the Trust Registry (TMS) integration required by Trust Protocol 2.0.
 * <p>
 * These properties control the TMS API connection as well as the in-memory Caffeine cache
 * behaviour for {@code idTS} and {@code pvaTS} trust statements.
 * <p>
 * The entire TP2.0 integration is conditionally enabled: if {@code api-url} is not configured,
 * the system degrades gracefully and no trust statements are injected into the Authorization Request.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "swiyu.trust-registry")
public class TrustRegistryProperties {

    /**
     * Base URL of the Trust Registry (TMS) API.
     * When absent, the TP2.0 integration is disabled.
     */
    private String apiUrl;

    /**
     * HTTP Basic Auth username (customer key) for authenticating against the TMS API.
     */
    private String customerKey;

    /**
     * HTTP Basic Auth password (customer secret) for authenticating against the TMS API.
     */
    private String customerSecret;

    /**
     * Maximum number of entries held in the trust statement cache.
     */
    private long maxCacheSize = 500;

    /**
     * Buffer in seconds subtracted from the JWT {@code exp} claim to compute the effective cache TTL.
     * Compensates for clock skew between the verifier and the TMS issuer.
     */
    private long clockSkewBufferSeconds = 30;

    /**
     * Hard cap for the cache TTL in seconds. The effective TTL is
     * {@code min(exp-based TTL, maxCacheTtlSeconds)}.
     * Set to {@code 0} to disable the cap.
     */
    private long maxCacheTtlSeconds = 3600;
}

