package com.example.app.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for {@link RedisCache}. Redis is mocked, so these run with no server and no
 * Docker - the behaviour under test is the caching logic, not Redis itself.
 */
@ExtendWith(MockitoExtension.class)
class RedisCacheTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    /** Small payload so the tests do not depend on java.time Jackson modules. */
    private record Widget(String id, String name) {}

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisCache cache;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        cache = new RedisCache(
                redis,
                new ObjectMapper(),
                new CacheProperties("test", TTL, Duration.ofSeconds(10)));
    }

    @Test
    @DisplayName("get() returns empty when the key is absent")
    void getReturnsEmptyOnMiss() {
        when(valueOps.get("test:widget:1")).thenReturn(null);

        assertThat(cache.get("widget:1", Widget.class)).isEmpty();
        assertThat(cache.stats().misses()).isEqualTo(1);
    }

    @Test
    @DisplayName("get() deserialises a stored value and prefixes the key with the namespace")
    void getReturnsCachedValue() {
        when(valueOps.get("test:widget:1")).thenReturn("{\"id\":\"1\",\"name\":\"Gear\"}");

        Optional<Widget> result = cache.get("widget:1", Widget.class);

        assertThat(result).contains(new Widget("1", "Gear"));
        assertThat(cache.stats().hits()).isEqualTo(1);
    }

    @Test
    @DisplayName("getOrLoad() invokes the loader on a miss and stores the result with the TTL")
    void getOrLoadPopulatesCacheOnMiss() {
        when(valueOps.get(anyString())).thenReturn(null);

        Widget result = cache.getOrLoad("widget:1", Widget.class, TTL,
                () -> new Widget("1", "Gear"));

        assertThat(result).isEqualTo(new Widget("1", "Gear"));
        verify(valueOps).set(eq("test:widget:1"), anyString(), eq(TTL));
    }

    @Test
    @DisplayName("getOrLoad() does not invoke the loader on a hit")
    void getOrLoadSkipsLoaderOnHit() {
        when(valueOps.get(anyString())).thenReturn("{\"id\":\"1\",\"name\":\"Gear\"}");
        AtomicInteger loaderCalls = new AtomicInteger();

        Widget result = cache.getOrLoad("widget:1", Widget.class, TTL, () -> {
            loaderCalls.incrementAndGet();
            return new Widget("1", "Fresh");
        });

        assertThat(result.name()).isEqualTo("Gear");
        assertThat(loaderCalls.get()).isZero();
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("a null from the loader is returned but not cached")
    void nullLoaderResultIsNotCached() {
        when(valueOps.get(anyString())).thenReturn(null);

        Widget result = cache.getOrLoad("widget:missing", Widget.class, TTL, () -> null);

        assertThat(result).isNull();
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("a Redis outage degrades to the loader instead of propagating the failure")
    void redisFailureDegradesGracefully() {
        when(valueOps.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        Widget result = cache.getOrLoad("widget:1", Widget.class, TTL,
                () -> new Widget("1", "FromSource"));

        assertThat(result).isEqualTo(new Widget("1", "FromSource"));
        assertThat(cache.stats().errors()).isPositive();
    }

    @Test
    @DisplayName("malformed cached JSON is treated as a miss rather than throwing")
    void corruptValueIsTreatedAsMiss() {
        when(valueOps.get(anyString())).thenReturn("not json at all");

        assertThat(cache.get("widget:1", Widget.class)).isEmpty();
        assertThat(cache.stats().errors()).isEqualTo(1);
    }

    @Test
    @DisplayName("a write failure is swallowed so the caller still gets its value")
    void writeFailureDoesNotBreakTheCall() {
        when(valueOps.get(anyString())).thenReturn(null);
        lenient().doThrow(new RedisConnectionFailureException("connection refused"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        Widget result = cache.getOrLoad("widget:1", Widget.class, TTL,
                () -> new Widget("1", "Gear"));

        assertThat(result).isEqualTo(new Widget("1", "Gear"));
    }

    @Test
    @DisplayName("CacheProperties applies its defaults when properties are absent")
    void cachePropertiesDefaults() {
        CacheProperties defaults = new CacheProperties(null, null, null);

        assertThat(defaults.namespace()).isEqualTo("learning-java");
        assertThat(defaults.defaultTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(defaults.lockTtl()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("stats() reports a hit ratio")
    void statsReportHitRatio() {
        when(valueOps.get("test:a")).thenReturn("{\"id\":\"1\",\"name\":\"Gear\"}");
        when(valueOps.get("test:b")).thenReturn(null);

        cache.get("a", Widget.class);
        cache.get("b", Widget.class);

        assertThat(cache.stats().hitRatio()).isEqualTo(0.5);
    }
}
