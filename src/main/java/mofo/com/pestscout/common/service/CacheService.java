package mofo.com.pestscout.common.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.config.RedisCacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for programmatic cache management and invalidation.
 * <p>
 * Provides utilities for:
 * - Complex cache key generation
 * - Targeted cache invalidation
 * - Pattern-based cache clearing
 * - Cache health monitoring
 * <p>
 * Use this service when declarative caching (@Cacheable) is insufficient
 * or when you need fine-grained control over cache invalidation.
 */
@Service
@RequiredArgsConstructor

public class CacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheService.class);

    private final CacheManager cacheManager;

    /**
     * Clear all farm-related caches when farm data changes.
     * This ensures downstream data (greenhouses, sessions, etc.) is refreshed.
     *
     * @param farmId the farm whose caches should be cleared
     */
    public void evictFarmCaches(UUID farmId) {
        LOGGER.info("Evicting all caches for farm {}", farmId);

        clearCache(RedisCacheConfig.CACHE_FARMS);
        clearCache(RedisCacheConfig.CACHE_GREENHOUSES);
        clearCache(RedisCacheConfig.CACHE_FIELD_BLOCKS);
        clearCache(RedisCacheConfig.CACHE_SESSIONS_LIST);

        // Analytics and heatmap caches use composite keys, clear all for this farm
        evictCachesByPrefix(RedisCacheConfig.CACHE_ANALYTICS, farmId.toString());
        evictCachesByPrefix(RedisCacheConfig.CACHE_HEATMAP, farmId.toString());

        LOGGER.debug("Cleared all farm-related caches for farm {}", farmId);
    }

    /**
     * Clear all session-related caches when a session is modified.
     *
     * @param farmId    the farm the session belongs to
     * @param sessionId the session that was modified
     */
    public void evictSessionCaches(UUID farmId, UUID sessionId) {
        LOGGER.info("Evicting session caches for session {} in farm {}", sessionId, farmId);

        clearCache(RedisCacheConfig.CACHE_SESSION_DETAIL);
        clearCache(RedisCacheConfig.CACHE_SESSIONS_LIST);

        // Session changes affect analytics and heatmaps
        evictCachesByPrefix(RedisCacheConfig.CACHE_ANALYTICS, farmId.toString());
        evictCachesByPrefix(RedisCacheConfig.CACHE_HEATMAP, farmId.toString());

        LOGGER.debug("Cleared session-related caches for session {}", sessionId);
    }

    /**
     * Clear analytics caches for a specific farm and time period.
     *
     * @param farmId the farm
     * @param week   ISO week number
     * @param year   year
     */
    public void evictAnalyticsCaches(UUID farmId, int week, int year) {
        String analyticsKey = buildAnalyticsKey(farmId, week, year);
        String heatmapKey = buildHeatmapKey(farmId, week, year);

        LOGGER.info("Evicting analytics caches for farm {} week {} year {}", farmId, week, year);

        evictCache(RedisCacheConfig.CACHE_ANALYTICS, analyticsKey);
        evictCache(RedisCacheConfig.CACHE_HEATMAP, heatmapKey);
    }

    /**
     * Clear user-related caches when user data changes.
     *
     * @param userId the user whose cache should be cleared
     */
    public void evictUserCache(UUID userId) {
        LOGGER.info("Evicting user cache for user {}", userId);
        // User cache keys include the requesting user id (for permission-aware responses).
        // Because we cannot enumerate every requester combination here, clear by prefix
        // to avoid leaving behind stale permission-scoped entries after updates.
        evictCachesByPrefix(RedisCacheConfig.CACHE_USERS, userId.toString());
    }

    /**
     * Clear all caches (use sparingly, typically only in admin operations or testing).
     */
    public void evictAllCaches() {
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

    /**
     * Build a composite cache key for analytics queries.
     *
     * @param farmId farm identifier
     * @param week   ISO week number
     * @param year   year
     * @return composite cache key
     */
    public String buildAnalyticsKey(UUID farmId, int week, int year) {
        return String.format("%s::week=%d::year=%d", farmId, week, year);
    }

    /**
     * Build a composite cache key for heatmap queries.
     *
     * @param farmId farm identifier
     * @param week   ISO week number
     * @param year   year
     * @return composite cache key
     */
    public String buildHeatmapKey(UUID farmId, int week, int year) {
        return String.format("%s::week=%d::year=%d", farmId, week, year);
    }

    /**
     * Evict a single cache entry by exact key.
     *
     * @param cacheName name of the cache
     * @param key       the cache key
     */
    private void evictCache(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            LOGGER.trace("Evicted cache entry: {} -> {}", cacheName, key);
        } else {
            LOGGER.warn("Cache not found: {}", cacheName);
        }
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            LOGGER.trace("Cleared entire cache: {}", cacheName);
        } else {
            LOGGER.warn("Cache not found: {}", cacheName);
        }
    }

    /**
     * Evict all cache entries matching a key prefix.
     * <p>
     * Note: This requires iterating all keys, which can be expensive.
     * Use sparingly and consider more targeted eviction strategies.
     *
     * @param cacheName name of the cache
     * @param keyPrefix prefix to match
     */
    private void evictCachesByPrefix(String cacheName, String keyPrefix) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            // Spring Cache doesn't provide pattern-based eviction out of the box
            // For now, we clear the entire cache - consider implementing a custom
            // RedisCacheManager if you need more granular pattern matching
            cache.clear();
            LOGGER.debug("Cleared entire cache {} (prefix eviction not supported, cleared all)", cacheName);
        } else {
            LOGGER.warn("Cache not found: {}", cacheName);
        }
    }

    /**
     * Check if a value exists in cache (useful for monitoring/debugging).
     *
     * @param cacheName name of the cache
     * @param key       the cache key
     * @return true if the value exists in cache
     */
    public boolean isCached(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(key);
            return wrapper != null && wrapper.get() != null;
        }
        return false;
    }

    /**
     * Get cache statistics for monitoring (requires a supporting CacheManager).
     * <p>
     * Returns basic cache health information for operations monitoring.
     */
    public CacheStats getCacheStats() {
        long totalCaches = cacheManager.getCacheNames().size();

        LOGGER.debug("Retrieved cache statistics: {} caches configured", totalCaches);

        return new CacheStats(
                totalCaches,
                cacheManager.getCacheNames()
        );
    }

    /**
     * Simple DTO for cache statistics.
     */
    public record CacheStats(
            long totalCaches,
            java.util.Collection<String> cacheNames
    ) {
    }
}
