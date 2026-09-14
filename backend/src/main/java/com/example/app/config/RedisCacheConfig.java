package com.example.app.config;

import com.example.app.cache.CacheProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the Redis cache.
 *
 * <p>Deliberately almost empty. Two things that might be expected here are not needed:
 *
 * <ul>
 *   <li><b>No {@code RedisTemplate} bean.</b> {@code spring-boot-starter-data-redis}
 *       already auto-configures {@link org.springframework.data.redis.core.StringRedisTemplate},
 *       which <i>is</i> a {@code RedisTemplate<String, String>} with string serializers on
 *       keys and values - exactly what {@code RedisCache} needs. Declaring another one
 *       would just shadow it.
 *   <li><b>No {@code @EnableCaching} / {@code CacheManager}.</b> This application uses
 *       {@code RedisCache} directly rather than Spring's cache abstraction, so the
 *       annotation would add proxying for nothing.
 * </ul>
 *
 * <p>There is also no {@code ObjectMapper} bean here, and that omission is load-bearing:
 * Spring Boot's auto-configured {@code ObjectMapper} is {@code @ConditionalOnMissingBean},
 * so declaring one of our own would <i>replace</i> the mapper used to serialise every
 * HTTP response. {@code RedisCache} takes a {@code copy()} of Boot's mapper instead.
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class RedisCacheConfig {
    // Configuration is entirely property-driven; see app.cache.* and spring.data.redis.*
}
