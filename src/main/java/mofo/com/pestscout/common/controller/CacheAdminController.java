package mofo.com.pestscout.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin endpoints for cache management and monitoring.
 * <p>
 * Provides operations for:
 * - Viewing cache statistics
 * - Clearing all caches
 * - Targeted cache invalidation
 * - Cache health monitoring
 * <p>
 * All endpoints require SUPER_ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
@Tag(name = "Cache Management", description = "Admin endpoints for cache operations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class CacheAdminController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheAdminController.class);

    private final CacheService cacheService;

    /**
     * Get cache statistics including configured caches and their names.
     */
    @GetMapping("/stats")
    @Operation(
            summary = "Get cache statistics",
            description = "Returns statistics about configured caches"
    )
    public ResponseEntity<CacheService.CacheStats> getCacheStats() {
        LOGGER.info("GET /api/admin/cache/stats - retrieving cache statistics");
        CacheService.CacheStats stats = cacheService.getCacheStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Clear all caches (expensive operation, use with caution).
     */
    @PostMapping("/clear-all")
    @Operation(
            summary = "Clear all caches",
            description = "Evicts all entries from all caches. Use with caution in production."
    )
    public ResponseEntity<ClearCacheResponse> clearAllCaches() {
        LOGGER.warn("POST /api/admin/cache/clear-all - clearing ALL caches");
        cacheService.evictAllCaches();

        return ResponseEntity.ok(new ClearCacheResponse(
                "All caches cleared successfully",
                cacheService.getCacheStats().totalCaches()
        ));
    }

    /**
     * Clear all caches related to a specific farm.
     */
    @PostMapping("/clear-farm/{farmId}")
    @Operation(
            summary = "Clear farm caches",
            description = "Evicts all cache entries related to the specified farm"
    )
    public ResponseEntity<ClearCacheResponse> clearFarmCaches(@PathVariable UUID farmId) {
        LOGGER.info("POST /api/admin/cache/clear-farm/{} - clearing farm caches", farmId);
        cacheService.evictFarmCaches(farmId);

        return ResponseEntity.ok(new ClearCacheResponse(
                "Farm caches cleared successfully for farm: " + farmId,
                6 // farms, greenhouses, field-blocks, sessions-list, analytics, heatmap
        ));
    }

    /**
     * Clear all caches related to a specific user.
     */
    @PostMapping("/clear-user/{userId}")
    @Operation(
            summary = "Clear user caches",
            description = "Evicts cache entries for the specified user"
    )
    public ResponseEntity<ClearCacheResponse> clearUserCache(@PathVariable UUID userId) {
        LOGGER.info("POST /api/admin/cache/clear-user/{} - clearing user cache", userId);
        cacheService.evictUserCache(userId);

        return ResponseEntity.ok(new ClearCacheResponse(
                "User cache cleared successfully for user: " + userId,
                1
        ));
    }

    /**
     * Clear analytics caches for a specific farm and time period.
     */
    @PostMapping("/clear-analytics")
    @Operation(
            summary = "Clear analytics caches",
            description = "Evicts analytics and heatmap caches for the specified farm and time period"
    )
    public ResponseEntity<ClearCacheResponse> clearAnalyticsCaches(
            @RequestParam UUID farmId,
            @RequestParam int week,
            @RequestParam int year) {

        LOGGER.info("POST /api/admin/cache/clear-analytics - farmId={}, week={}, year={}",
                farmId, week, year);

        cacheService.evictAnalyticsCaches(farmId, week, year);

        return ResponseEntity.ok(new ClearCacheResponse(
                String.format("Analytics caches cleared for farm %s, week %d, year %d",
                        farmId, week, year),
                2 // analytics + heatmap
        ));
    }

    /**
     * Check if a specific cache key exists (for debugging).
     */
    @GetMapping("/check")
    @Operation(
            summary = "Check cache key",
            description = "Checks if a specific key exists in the given cache"
    )
    public ResponseEntity<CacheCheckResponse> checkCacheKey(
            @RequestParam String cacheName,
            @RequestParam String key) {

        LOGGER.debug("GET /api/admin/cache/check - cacheName={}, key={}", cacheName, key);

        boolean exists = cacheService.isCached(cacheName, key);

        return ResponseEntity.ok(new CacheCheckResponse(
                cacheName,
                key,
                exists
        ));
    }

    /**
     * Response DTO for cache clearing operations.
     */
    public record ClearCacheResponse(
            String message,
            long cachesCleared
    ) {
    }

    /**
     * Response DTO for cache key checks.
     */
    public record CacheCheckResponse(
            String cacheName,
            String key,
            boolean exists
    ) {
    }
}
