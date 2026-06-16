package mofo.com.pestscout.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(cacheManager);
        lenient().when(cacheManager.getCache(ArgumentMatchers.anyString())).thenReturn(cache);
        lenient().when(cacheManager.getCacheNames()).thenReturn(List.of("analytics", "heatmap"));
    }

    @Test
    void evictFarmCachesClearsAllRelatedCaches() {
        UUID farmId = UUID.randomUUID();

        cacheService.evictFarmCaches(farmId);

        verify(cache, times(6)).clear();
    }

    @Test
    void evictAnalyticsCachesClearsAnalyticsAndHeatmapCaches() {
        UUID farmId = UUID.randomUUID();

        cacheService.evictAnalyticsCaches(farmId, 4, 2024);

        verify(cache, times(2)).clear();
    }

    @Test
    void isCachedReturnsTrueWhenValuePresent() {
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        when(wrapper.get()).thenReturn("value");
        when(cache.get("foo")).thenReturn(wrapper);

        boolean cached = cacheService.isCached("analytics", "foo");

        assertThat(cached).isTrue();
    }

    @Test
    void getCacheStatsReturnsConfiguredNames() {
        CacheService.CacheStats stats = cacheService.getCacheStats();

        assertThat(stats.totalCaches()).isEqualTo(2);
        assertThat(stats.cacheNames()).containsExactlyInAnyOrder("analytics", "heatmap");
    }

}
