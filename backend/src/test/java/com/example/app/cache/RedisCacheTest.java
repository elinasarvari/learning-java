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


    // ------------------------------------------------------------------
    // String values and comparison
    // ------------------------------------------------------------------

    @Test
    @DisplayName("putString stores the value verbatim, with no JSON quoting")
    void putStringStoresVerbatim() {
        cache.putString("otp:user-1", "483920", Duration.ofMinutes(5));

        verify(valueOps).set("test:otp:user-1", "483920", Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("put() JSON-encodes a String, which is why putString exists")
    void putJsonEncodesStrings() {
        cache.put("otp:user-1", "483920", Duration.ofMinutes(5));

        // Quoted - comparing this against the raw parameter would never match.
        verify(valueOps).set("test:otp:user-1", "\"483920\"", Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("matches() is true only when the stored value is identical")
    void matchesComparesStoredValue() {
        when(valueOps.get("test:otp:user-1")).thenReturn("483920");

        assertThat(cache.matches("otp:user-1", "483920")).isTrue();
        assertThat(cache.matches("otp:user-1", "000000")).isFalse();
    }

    @Test
    @DisplayName("matches() is false for a missing key rather than an error")
    void matchesIsFalseWhenKeyAbsent() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThat(cache.matches("otp:expired", "483920")).isFalse();
    }

    @Test
    @DisplayName("matches() is false for a null candidate and never touches Redis")
    void matchesRejectsNullCandidate() {
        assertThat(cache.matches("otp:user-1", null)).isFalse();
        verify(valueOps, never()).get(anyString());
    }

    @Test
    @DisplayName("consumeIfMatches() uses GETDEL so a correct value works exactly once")
    void consumeIfMatchesDeletesOnSuccess() {
        when(valueOps.getAndDelete("test:otp:user-1")).thenReturn("483920");

        assertThat(cache.consumeIfMatches("otp:user-1", "483920")).isTrue();
        verify(valueOps).getAndDelete("test:otp:user-1");
    }

    @Test
    @DisplayName("consumeIfMatches() is false when the key is already consumed")
    void consumeIfMatchesFailsOnSecondUse() {
        when(valueOps.getAndDelete(anyString())).thenReturn(null);

        assertThat(cache.consumeIfMatches("otp:user-1", "483920")).isFalse();
    }

    @Test
    @DisplayName("timeToLive() returns empty when the key has no expiry")
    void timeToLiveEmptyWithoutExpiry() {
        when(redis.getExpire("test:otp:user-1")).thenReturn(-1L);

        assertThat(cache.timeToLive("otp:user-1")).isEmpty();
    }

    @Test
    @DisplayName("timeToLive() reports the remaining window")
    void timeToLiveReportsRemaining() {
        when(redis.getExpire("test:otp:user-1")).thenReturn(287L);

        assertThat(cache.timeToLive("otp:user-1")).contains(Duration.ofSeconds(287));
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
