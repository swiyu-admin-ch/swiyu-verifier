package ch.admin.bj.swiyu.verifier.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.STATUS_LIST_CACHE;

@Slf4j
@Configuration
@Conditional(StatusListCachingCondition.class)
public class StatusListCache {

    /**
     * This method is scheduled to run at a fixed rate defined by the property
     * 'caching.spring.statusListCacheTTL'. It clears all entries in the 'statusListCache'.
     */
    @CacheEvict(value = STATUS_LIST_CACHE, allEntries = true)
    @Scheduled(fixedRateString = "10000")
    @Conditional(StatusListCachingCondition.class)
    public void emptyStatusListCache() {
        log.debug("emptying status list cache");
    }
}