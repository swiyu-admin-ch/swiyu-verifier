package ch.admin.bj.swiyu.verifier.common.config.trust;

import java.net.URL;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "swiyu.trust-registry")
public record TrustRegistryProperties(
            @NotNull URL apiUrl,
            @NotNull String customerKey,
            @NotNull String customerSecret,
            long maxCacheSize,
            long clockSkewBufferSeconds,
            long maxCacheTtlSeconds
) {
}
