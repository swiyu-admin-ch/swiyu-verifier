package ch.admin.bj.swiyu.verifier.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.JWK_CACHE;
import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.TRUST_STATEMENT_CACHE;
/**
 * Handles scheduled eviction of Spring caches.
 * Cache names are configured via {@link CachingConfig}.
 */
@Slf4j
@Component
public class CacheCustomizer {

    /**
     * This method is scheduled to run at a fixed rate defined by the property
     * 'caching.jwk-cache-ttl-ms'. It clears all entries in the 'JWK_CACHE'.
     */
    @CacheEvict(value = JWK_CACHE, allEntries = true)
    @Scheduled(fixedRateString = "${caching.jwk-cache-ttl-ms:${caching.jwk-cache-ttl:3600000}}")
    public void emptyIssuerPublicKeyCache() {
        log.debug("emptying public keys cache");
    }

    @CacheEvict(value = TRUST_STATEMENT_CACHE, allEntries = true)
    @Scheduled(fixedRateString = "${caching.trust-cache-ttl-ms:${caching.trust-cache-ttl:3600000}}")
    public void emptyTrustCache() {
        log.debug("emptying trust statement cache");
    }
}