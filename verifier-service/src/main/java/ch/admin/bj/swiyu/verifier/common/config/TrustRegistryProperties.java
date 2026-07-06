package ch.admin.bj.swiyu.verifier.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URL;
import java.time.Duration;

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

    /**
     * Base URL of the TMS CBS Authoring API used for On-the-Fly vqPS registration.
     * When absent, the vqPS registration flow is disabled and no vqPS will be injected.
     * Example: {@code https://tms.example.com}
     */
    private URL tmsAuthoringUrl;

    /**
     * OAuth2 token endpoint URL for obtaining a bearer token to authenticate
     * against the TMS Authoring API.
     * Example: {@code https://eportal.example.com/oauth/token}
     */
    private URL oauthTokenUrl;

    /**
     * OAuth2 client_id for the client_credentials grant used to obtain access tokens.
     */
    private String oauthClientId;

    /**
     * OAuth2 client_secret for the client_credentials grant.
     */
    private String oauthClientSecret;

    /**
     * Buffer in seconds subtracted from the current verification TTL when checking
     * whether a cached vqPS is still valid. Ensures the vqPS does not expire before
     * the verification session itself expires.
     * Default: 60 seconds.
     */
    private long vqpsExpiryBufferSeconds = 60;

    /**
     * A static refresh token used to bootstrap the first token set on application startup.
     * Must be treated as a secret and never logged.
     */
    private String bootstrapRefreshToken;

    /**
     * Interval between proactive OAuth2 token refreshes performed by the scheduler.
     * Should be shorter than the refresh-token lifetime to avoid expiry.
     * Default: {@code PT12H} (12 hours).
     */
    private Duration tokenRefreshInterval;
}

