package mofo.com.pestscout.common.service;

import mofo.com.pestscout.common.config.SimpleCacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.UUID;

/**
 * Service for programmatic cache management and invalidation.
 * Safe to run even if caching features are completely disabled.
 */
@Service
public class CacheService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheService.class);
    private final CacheManager cacheManager;

    // Optional constructor injection - prevents startup crashes if CacheManager is missing
    public CacheService(@Autowired(required = false) CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        if (cacheManager == null) {
            LOGGER.info("CacheManager bean not found. Caching service is operating in BYPASS mode.");
        }
    }

    public void evictFarmCaches(UUID farmId) {
        if (cacheManager == null) return;
        LOGGER.info("Evicting all caches for farm {}", farmId);
        clearCache(SimpleCacheConfig.CACHE_FARMS);
        clearCache(SimpleCacheConfig.CACHE_GREENHOUSES);
        clearCache(SimpleCacheConfig.CACHE_FIELD_BLOCKS);
        clearCache(SimpleCacheConfig.CACHE_SESSIONS_LIST);

        evictCachesByPrefix(SimpleCacheConfig.CACHE_ANALYTICS, farmId.toString());
        evictCachesByPrefix(SimpleCacheConfig.CACHE_HEATMAP, farmId.toString());
        LOGGER.debug("Cleared all farm-related caches for farm {}", farmId);
    }

    public void evictFarmCachesAfterCommit(UUID farmId) {
        runAfterCommit(() -> evictFarmCaches(farmId));
    }

    public void evictSessionCaches(UUID farmId, UUID sessionId) {
        if (cacheManager == null) return;
        LOGGER.info("Evicting session caches for session {} in farm {}", sessionId, farmId);
        clearCache(SimpleCacheConfig.CACHE_SESSION_DETAIL);
        clearCache(SimpleCacheConfig.CACHE_SESSIONS_LIST);

        evictCachesByPrefix(SimpleCacheConfig.CACHE_ANALYTICS, farmId.toString());
        evictCachesByPrefix(SimpleCacheConfig.CACHE_HEATMAP, farmId.toString());
        LOGGER.debug("Cleared session-related caches for session {}", sessionId);
    }

    public void evictSessionCachesAfterCommit(UUID farmId, UUID sessionId) {
        runAfterCommit(() -> evictSessionCaches(farmId, sessionId));
    }

    public void evictAnalyticsCaches(UUID farmId, int week, int year) {
        if (cacheManager == null) return;
        LOGGER.info("Evicting analytics caches for farm {} week {} year {}", farmId, week, year);
        clearCache(SimpleCacheConfig.CACHE_ANALYTICS);
        clearCache(SimpleCacheConfig.CACHE_HEATMAP);
    }

    public void evictUserCache(UUID userId) {
        if (cacheManager == null) return;
        LOGGER.info("Evicting user cache for user {}", userId);
        evictCachesByPrefix(SimpleCacheConfig.CACHE_USERS, userId.toString());
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    public void evictAllCaches() {
        if (cacheManager == null) return;
        LOGGER.warn("Evicting ALL caches - this is expensive and should be used sparingly");
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                LOGGER.debug("Cleared cache: {}", cacheName);
            }
        });
        LOGGER.info("All caches cleared");
    }

    private void evictCache(String cacheName, String key) {
        if (cacheManager == null) return;
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            LOGGER.trace("Evicted cache entry: {} -> {}", cacheName, key);
        } else {
            LOGGER.warn("Cache not found: {}", cacheName);
        }
    }

    private void clearCache(String cacheName) {
        if (cacheManager == null) return;
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            LOGGER.trace("Cleared entire cache: {}", cacheName);
        } else {
            LOGGER.warn("Cache not found: {}", cacheName);
        }
    }

    private void evictCachesByPrefix(String cacheName, String keyPrefix) {
        if (cacheManager == null) return;
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            LOGGER.debug("Cleared entire cache {} (prefix eviction not supported, cleared all)", cacheName);
        } else {
            LOGGER.warn("Cache not found: {}", cacheName);
        }
    }

    public boolean isCached(String cacheName, String key) {
        if (cacheManager == null) return false;
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(key);
            return wrapper != null && wrapper.get() != null;
        }
        return false;
    }

    public CacheStats getCacheStats() {
        if (cacheManager == null) {
            return new CacheStats(0, Collections.emptyList());
        }
        long totalCaches = cacheManager.getCacheNames().size();
        LOGGER.debug("Retrieved cache statistics: {} caches configured", totalCaches);
        return new CacheStats(totalCaches, cacheManager.getCacheNames());
    }

    public record CacheStats(long totalCaches, java.util.Collection<String> cacheNames) {}
}
