package ch.admin.bj.swiyu.verifier.service.trustregistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.JWK_CACHE;

/**
 * Service for manually clearing selected application caches.
 *
 * <p>This component centralizes explicit cache invalidation for caches that may need
 * to be refreshed outside their normal expiration policy, for example after key rotations
 * at the Trust Registry or after a failed trust statement signature verification.</p>
 *
 * <p>Only targeted, per-entry eviction is exposed intentionally – a full cache clear would affect
 * all cached entries simultaneously and is therefore not provided (blast radius principle).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheMaintenanceService {

    private final CacheManager cacheManager;

    /**
     * Clears the JWK cache for a specific DID URL (kid).
     *
     * <p>Call this when key rotation is detected or a trust statement signature verification fails,
     * to ensure the rotated JWK is reloaded from the DID Document on the next resolution.</p>
     *
     * @param kid the key identifier (DID URL) whose cached JWK should be evicted
     */
    public void evictJwkManually(String kid) {
        var cache = cacheManager.getCache(JWK_CACHE);
        if (cache != null) {
            log.debug("Evicting JWK cache for kid={}", kid);
            cache.evict(kid);
        }
    }
}
