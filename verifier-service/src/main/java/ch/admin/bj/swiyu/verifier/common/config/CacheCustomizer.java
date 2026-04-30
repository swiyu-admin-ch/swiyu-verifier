package ch.admin.bj.swiyu.verifier.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.ISSUER_PUBLIC_KEY_CACHE;

/**
 * Handles scheduled eviction of Spring caches.
 * Cache names are configured via {@link CachingConfig}.
 */
@Slf4j
@Component
public class CacheCustomizer {

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