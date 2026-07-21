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
    private long statusListCacheSize = 1000l;
 
    /**
     * Cache Timeout time in milliseconds for token status lists
     */
    @NotNull
    private Long statusListCacheTtl = 0l;

    /**
     * Cache Timeout time in milliseconds for public keys fetched to perform a verification
     */
    @NotNull
    private Long jwkCacheTtl = 3_600_000l;


    /**
     * Backoff when no valid Status List or Trust Statement is found or the trust statement is not valid.
     */
    private long requestBackoffSeconds = 600;
}