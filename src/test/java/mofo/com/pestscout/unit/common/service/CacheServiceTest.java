package mofo.com.pestscout.unit.common.service;

import mofo.com.pestscout.common.service.CacheService;
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

    @Mock
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
    void evictAnalyticsCachesUsesCompositeKeys() {
        UUID farmId = UUID.randomUUID();

        CacheService spyService = spy(cacheService);
        doReturn("key").when(spyService).buildAnalyticsKey(farmId, 4, 2024);
        doReturn("heatmapKey").when(spyService).buildHeatmapKey(farmId, 4, 2024);

        spyService.evictAnalyticsCaches(farmId, 4, 2024);

        verify(cache).evict("key");
        verify(cache).evict("heatmapKey");
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
    void buildKeyHelpersFormatCompositeKeys() {
        UUID farmId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        assertThat(cacheService.buildAnalyticsKey(farmId, 12, 2023))
                .isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa::week=12::year=2023");
        assertThat(cacheService.buildHeatmapKey(farmId, 2, 2025))
                .isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa::week=2::year=2025");
    }

    @Test
    void getCacheStatsReturnsConfiguredNames() {
        CacheService.CacheStats stats = cacheService.getCacheStats();

        assertThat(stats.totalCaches()).isEqualTo(2);
        assertThat(stats.cacheNames()).containsExactlyInAnyOrder("analytics", "heatmap");
    }
}
