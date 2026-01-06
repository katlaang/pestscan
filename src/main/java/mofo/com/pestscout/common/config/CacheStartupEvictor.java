package mofo.com.pestscout.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.common.service.CacheService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Clears all caches at startup to ensure no stale entries remain from prior
 * deployments (e.g., cache keys that changed). This is particularly important
 * when cache key expressions evolve to include user-scoped identifiers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheStartupEvictor {

    private final CacheService cacheService;

    @EventListener(ApplicationReadyEvent.class)
    public void clearCachesOnStartup() {
        log.info("Clearing caches at startup to remove stale entries");
        cacheService.evictAllCaches();
    }
}
