package ch.admin.bj.swiyu.verifier.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.STATUS_LIST_CACHE;

@Slf4j
@Configuration
@ConditionalOnExpression("${caching.status-list-cache-ttl:0} > 0")
public class StatusListCache {

    /**
     * Evicts all entries from the status list cache at a fixed rate defined by
     * {@code caching.status-list-cache-ttl} (in milliseconds). This bean is only registered
     * when the property is explicitly set to a value greater than zero; a value of {@code 0}
     * (the default) disables caching entirely.
     */
    @CacheEvict(value = STATUS_LIST_CACHE, allEntries = true)
    @Scheduled(fixedRateString = "${caching.status-list-cache-ttl}")
    public void emptyStatusListCache() {
        log.debug("emptying status list cache");
    }
}