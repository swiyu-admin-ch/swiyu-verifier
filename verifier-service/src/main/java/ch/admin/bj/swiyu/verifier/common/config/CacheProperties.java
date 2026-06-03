package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@ConfigurationProperties(prefix = "caching")
public class CacheProperties {

    @NotNull
    private Long statusListCacheTtl;

    @NotNull
    private Long issuerPublicKeyCacheTtl;
}