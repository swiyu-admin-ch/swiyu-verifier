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
     * DID identifier of this verifier, used to fetch {@code idTS} and {@code pvaTS} from the TMS.
     */
    private String verifierDid;

    /**
     * JTI (UUID) of the Protected Verification Authorization Trust Statement (pvaTS) to fetch.
     */
    private String pvaTsJti;

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
     * Short TTL in seconds used for negative cache entries when the TMS API call fails.
     * Prevents retry storms and thread exhaustion.
     */
    private long negativeCacheTtlSeconds = 30;

    /**
     * Base URL of the TMS CBS Authoring API used for On-the-Fly vqPS registration.
     * When absent, the vqPS registration flow is disabled and no vqPS will be injected.
     * Example: {@code https://tms.example.com}
     */
    private String tmsAuthoringUrl;

    /**
     * OAuth2 token endpoint URL for obtaining a bearer token to authenticate
     * against the TMS Authoring API.
     * Example: {@code https://eportal.example.com/oauth/token}
     */
    private String oauthTokenUrl;

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
}

