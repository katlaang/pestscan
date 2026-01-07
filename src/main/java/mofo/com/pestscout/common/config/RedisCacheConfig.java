package mofo.com.pestscout.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.farm.security.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache configuration for the Mofo Pest Scouting application.
 * Implements caching strategies for:
 * - User session data (short TTL)
 * - Farm/Greenhouse metadata (medium TTL)
 * - Analytics and heatmap data (short TTL, expensive queries)
 * - Static reference data (long TTL)
 * All cache entries are automatically serialized to JSON and include type information
 * to prevent deserialization issues.
 */

@Configuration
@EnableCaching
@Profile("!test")
@ConditionalOnProperty(
        prefix = "spring.cache",
        name = "type",
        havingValue = "redis",
        matchIfMissing = true
)
public class RedisCacheConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCacheConfig.class);

    /**
     * Cache names used throughout the application.
     * These constants ensure consistency across service layers.
     */
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

    /**
     * Configure cache manager with per-cache TTL settings.
     * <p>
     * TTL Strategy:
     * - Critical real-time data: 5-15 minutes
     * - Semi-static data: 1 hour
     * - Static reference data: 24 hours
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        LOGGER.info("Configuring Redis cache manager with custom TTL per cache");

        // Default cache configuration (15 minutes)
        RedisCacheConfiguration defaultConfig = createCacheConfiguration(Duration.ofMinutes(15));

        // Per-cache TTL configurations
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // User data - 1 hour (relatively stable, but needs refresh for permissions)
        cacheConfigurations.put(CACHE_USERS, createCacheConfiguration(Duration.ofHours(1)));

        // Farm metadata - 2 hours (rarely changes)
        cacheConfigurations.put(CACHE_FARMS, createCacheConfiguration(Duration.ofHours(2)));
        cacheConfigurations.put(CACHE_FARMS_LIST, createCacheConfiguration(Duration.ofHours(2)));

        // Greenhouse/Field block data - 1 hour (may change during farm setup)
        cacheConfigurations.put(CACHE_GREENHOUSES, createCacheConfiguration(Duration.ofHours(1)));
        cacheConfigurations.put(CACHE_FIELD_BLOCKS, createCacheConfiguration(Duration.ofHours(1)));

        // Analytics - 30 minutes (expensive queries, but data changes with new observations)
        cacheConfigurations.put(CACHE_ANALYTICS, createCacheConfiguration(Duration.ofMinutes(30)));

        // Heatmap - 15 minutes (critical for performance, but must reflect recent data)
        cacheConfigurations.put(CACHE_HEATMAP, createCacheConfiguration(Duration.ofMinutes(15)));

        // Species catalog - 24 hours (static reference data)
        cacheConfigurations.put(CACHE_SPECIES_CATALOG, createCacheConfiguration(Duration.ofHours(24)));

        // Session lists - 10 minutes (frequently updated as scouts work)
        cacheConfigurations.put(CACHE_SESSIONS_LIST, createCacheConfiguration(Duration.ofMinutes(10)));

        // Session detail - 15 minutes (detailed data, but may be edited)
        cacheConfigurations.put(CACHE_SESSION_DETAIL, createCacheConfiguration(Duration.ofMinutes(15)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware() // Support for transactional cache operations
                .build();

        LOGGER.info("Redis cache manager configured with {} custom cache configurations", cacheConfigurations.size());
        return cacheManager;
    }

    @Bean("tenantAwareKeyGenerator")
    public KeyGenerator tenantAwareKeyGenerator(CurrentUserService currentUserService) {
        return (target, method, params) -> {
            String tenant = "anonymous";
            String userId = "anonymous";
            String role = "anonymous";

            try {
                User user = currentUserService.getCurrentUser();
                tenant = user.getCustomerNumber();
                userId = user.getId() != null ? user.getId().toString() : "unknown";
                role = user.getRole() != null ? user.getRole().name() : "unknown";
            } catch (RuntimeException ex) {
                LOGGER.debug("Falling back to anonymous cache key for {}.{}", target.getClass().getSimpleName(), method.getName());
            }

            Object paramKey = SimpleKeyGenerator.generateKey(params);
            return String.format("%s::%s::%s::tenant=%s::user=%s::role=%s",
                    target.getClass().getSimpleName(),
                    method.getName(),
                    paramKey,
                    tenant,
                    userId,
                    role);
        };
    }

    /**
     * Create cache configuration with custom TTL and JSON serialization.
     *
     * @param ttl time-to-live duration for cache entries
     * @return configured RedisCacheConfiguration
     */
    private RedisCacheConfiguration createCacheConfiguration(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapperForCache())
                        )
                )
                .disableCachingNullValues(); // Don't cache null results
    }

    /**
     * ObjectMapper configured for Redis cache serialization.
     * <p>
     * Includes:
     * - Type information to prevent deserialization errors
     * - Java 8 time module for LocalDate/LocalDateTime support
     * - Validation to prevent security issues with polymorphic types
     */
    private ObjectMapper objectMapperForCache() {
        ObjectMapper mapper = new ObjectMapper();

        // Register Java 8 date/time support
        mapper.registerModule(new JavaTimeModule());

        // Enable type information for polymorphic deserialization
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        LOGGER.debug("Configured ObjectMapper for Redis cache with JavaTimeModule and type information");
        return mapper;
    }
}
