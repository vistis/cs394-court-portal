package kh.edu.paragoniu.court_portal.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Redis-backed cache-aside layer for the Greffier panel, per the system
 * architecture requirement: reads check the cache first (miss → DB → push),
 * writes evict the affected caches so subsequent reads repopulate from the DB.
 *
 * <p>Values are JDK-serialized (every cached type implements {@link
 * java.io.Serializable}), matching the serialization Spring Session already
 * uses on the same Redis instance. Each cache has a short TTL so any staleness
 * from a write in another module (or another panel) self-heals quickly — the
 * safety net behind the explicit {@code @CacheEvict} hooks.
 *
 * <p>Cache names are shared with the other panels' equivalent reads so the
 * data-handling stays consistent across court-portal / court-public /
 * court-admin. This config should eventually be promoted to court-shared.
 *
 * <p>{@code @EnableCaching} lives on {@code CourtPortalApplication}; this class
 * only supplies the {@link RedisCacheManager}, which replaces Spring Boot's
 * auto-configured one and therefore also governs the public-panel caches
 * ({@code publicCases}, {@code publicCaseDetail}, {@code publicHearings},
 * {@code caseDetail}). Null values stay allowed to match the default manager
 * those caches were written against.
 */
@Configuration
public class CacheConfig {

    // Volatile per-entity data — short TTL.
    private static final Duration LIST_TTL = Duration.ofSeconds(60);
    private static final Duration DETAIL_TTL = Duration.ofSeconds(120);
    // Slow-changing lookup/reference data (classifications, statuses, names).
    private static final Duration REF_TTL = Duration.ofMinutes(10);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(LIST_TTL)
            .serializeValuesWith(
                SerializationPair.fromSerializer(
                    // Pass the application classloader so JDK deserialization
                    // can resolve our DTO classes inside the Spring Boot fat jar.
                    RedisSerializer.java(getClass().getClassLoader())
                )
            );

        Map<String, RedisCacheConfiguration> caches = new HashMap<>();
        // Lists (paginated search results)
        caches.put("greffierList", base.entryTtl(LIST_TTL));
        caches.put("assignedCases", base.entryTtl(LIST_TTL));
        caches.put("hearingList", base.entryTtl(LIST_TTL));
        caches.put("caseList", base.entryTtl(LIST_TTL));
        caches.put("publicCases", base.entryTtl(LIST_TTL));
        caches.put("publicHearings", base.entryTtl(LIST_TTL));
        // Details (single records)
        caches.put("hearingDetail", base.entryTtl(DETAIL_TTL));
        caches.put("caseDetail", base.entryTtl(DETAIL_TTL));
        caches.put("publicCaseDetail", base.entryTtl(DETAIL_TTL));
        // Reference / lookup data
        caches.put("greffierNames", base.entryTtl(REF_TTL));
        caches.put("refData", base.entryTtl(REF_TTL));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(base)
            .withInitialCacheConfigurations(caches)
            .build();
    }
}
