package ch.admin.bj.swiyu.verifier.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CachingConfig {

    public static final String STATUS_LIST_CACHE = "statusListCache";
    public static final String ISSUER_PUBLIC_KEY_CACHE = "issuerPublicKeyCache";
    public static final String TRUST_STATEMENT_CACHE = "trustStatementCache";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(STATUS_LIST_CACHE, ISSUER_PUBLIC_KEY_CACHE, TRUST_STATEMENT_CACHE);
    }
}