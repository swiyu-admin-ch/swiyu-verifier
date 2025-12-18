package ch.admin.bj.swiyu.verifier.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.ISSUER_PUBLIC_KEY_CACHE;
import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.STATUS_LIST_CACHE;

@Slf4j
@Component
public class CacheCustomizer implements CacheManagerCustomizer<ConcurrentMapCacheManager> {

    @Override
    // Needed to customize the behavior of Spring's caching abstraction
    public void customize(ConcurrentMapCacheManager cacheManager) {
        cacheManager.setCacheNames(List.of(STATUS_LIST_CACHE, ISSUER_PUBLIC_KEY_CACHE));
    }

    /**
     * This method is scheduled to run at a fixed rate defined by the property
     * 'caching.spring.issuerPublicKeyCacheTTL'. It clears all entries in the 'issuerPublicKeyCache'.
     */
    @CacheEvict(value = ISSUER_PUBLIC_KEY_CACHE, allEntries = true)
    @Scheduled(fixedRateString = "${caching.issuer-public-key-cache-ttl}")
    public void emptyIssuerPublicKeyCache() {
        log.debug("emptying public keys cache");
    }
}