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
     * Cache Timeout time in milliseconds for token status lists
     */
    @NotNull
    private Long statusListCacheTtl = 0l;

    /**
     * Cache Timeout time in milliseconds for public keys fetched to perform a verification
     */
    @NotNull
    private Long jwkCacheTtl = 3_600_000l;
}