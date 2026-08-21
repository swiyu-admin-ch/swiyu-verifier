package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@ConfigurationProperties(prefix = "caching")
public class CacheProperties {

 
    /**
     * Maximum size of the status list cache
     */
    private long statusListCacheSize = 1000L;
 
    /**
     * Cache Timeout time in milliseconds for token status lists
     */
    @NotNull
    private Long statusListCacheTtlMs = 0L;

    /**
     * Legacy cache Timeout time in milliseconds for token status lists
     */
    @Deprecated
    private Long statusListCacheTtl;

    /**
     * Backoff when no valid Status List or Trust Statement is found or the trust statement is not valid.
     */
    private long requestBackoffSeconds = 600;

    /**
     * Backwards compatibility getter: if the legacy property
     * 'caching.status-list-cache-ttl' (without the '-ms' suffix) is present,
     * that legacy value will be used. Otherwise, the newer
     * 'caching.status-list-cache-ttl-ms' property is used. When both
     * properties are present the legacy property takes precedence.
     */
    public Long getStatusListCacheTtlMs() {
        return statusListCacheTtl != null ? statusListCacheTtl : statusListCacheTtlMs;
    }
}