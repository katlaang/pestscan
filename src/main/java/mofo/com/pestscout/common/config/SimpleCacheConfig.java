package mofo.com.pestscout.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@ConditionalOnProperty(
        name = "spring.cache.type",
        havingValue = "simple",
        matchIfMissing = true
)
public class SimpleCacheConfig {

    public static final String CACHE_USERS = "users";
    public static final String CACHE_FARMS = "farms";
    public static final String CACHE_FARMS_LIST = "farms-list";
    public static final String CACHE_GREENHOUSES = "greenhouses";
    public static final String CACHE_FIELD_BLOCKS = "field-blocks";
    public static final String CACHE_ANALYTICS = "analytics";
    public static final String CACHE_HEATMAP = "heatmap";
    public static final String CACHE_SPECIES_CATALOG = "species-catalog";
    public static final String CACHE_SESSIONS_LIST = "sessions-list";
    public static final String CACHE_SESSION_DETAIL = "session-detail";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                CACHE_USERS,
                CACHE_FARMS,
                CACHE_FARMS_LIST,
                CACHE_GREENHOUSES,
                CACHE_FIELD_BLOCKS,
                CACHE_ANALYTICS,
                CACHE_HEATMAP,
                CACHE_SPECIES_CATALOG,
                CACHE_SESSIONS_LIST,
                CACHE_SESSION_DETAIL
        );
    }
}

