package com.example.app.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis cache built directly on {@link StringRedisTemplate} - no {@code @Cacheable}.
 *
 * <p>Two families of operation, and mixing them is the easiest mistake to make:
 *
 * <ul>
 *   <li><b>Object cache</b> ({@code get}/{@code put}/{@code getOrLoad}) - values are
 *       JSON. Use for domain records.
 *   <li><b>String cache</b> ({@code getString}/{@code putString}/{@code matches}) - values
 *       are stored verbatim. Use for tokens, codes, flags - anything you compare rather
 *       than deserialise.
 * </ul>
 *
 * <p>A value written with {@code put("k", "abc")} is stored as {@code "abc"} <i>with
 * quotes</i>, because that is valid JSON for a string. {@code getString} would then
 * return those quotes and every comparison would fail. Pick one family per key.
 *
 * <p>Every operation degrades on failure: reads return empty, writes are dropped, both
 * logged at WARN. A Redis outage makes the application slower, not broken.
 *
 * <p>Design rationale and known gaps: {@code docs/architecture/caching-pattern.md}.
 */
@Component
public class RedisCache {

    private static final Logger log = LoggerFactory.getLogger(RedisCache.class);
    private static final int SCAN_BATCH = 500;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final CacheProperties properties;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public RedisCache(StringRedisTemplate redis, ObjectMapper objectMapper, CacheProperties properties) {
        this.redis = redis;
        this.properties = properties;
        // copy(): Boot's mapper also serialises every HTTP response, so configuring it
        // here would change the API wire format. Unknown properties are ignored because
        // cached JSON outlives deploys that remove a field.
        this.mapper = objectMapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    // ==================================================================
    // String values - compare, don't deserialise
    // ==================================================================

    /** Reads a value stored with {@link #putString}, verbatim. */
    public Optional<String> getString(String key) {
        try {
            String value = redis.opsForValue().get(namespaced(key));
            record(value != null);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            return failed("read", key, e, Optional.empty());
        }
    }

    /**
     * Stores a raw string.
     *
     * @return true when the write landed; false when Redis rejected or was unreachable.
     *     Callers that report success to a user must check this - a dropped write is
     *     invisible otherwise.
     */
    public boolean putString(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(namespaced(key), value, ttl);
            return true;
        } catch (Exception e) {
            return failed("write", key, e, false);
        }
    }

    public boolean putString(String key, String value) {
        return putString(key, value, properties.defaultTtl());
    }

    /**
     * True when the key exists and holds exactly {@code candidate}.
     *
     * <p>A missing key is false, never an error - an expired entry and a wrong value are
     * the same answer to the caller.
     *
     * <p>Comparison is constant-time, so the duration of a failed check does not reveal
     * how many leading characters were correct. Irrelevant for a cached ETag, necessary
     * the moment the value is a token or one-time code.
     */
    public boolean matches(String key, String candidate) {
        if (candidate == null) {
            return false;
        }
        return getString(key)
                .map(stored -> MessageDigest.isEqual(
                        stored.getBytes(StandardCharsets.UTF_8),
                        candidate.getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }

    /**
     * {@link #matches}, but deletes the key on a successful match.
     *
     * <p>For single-use values - one-time codes, idempotency keys, nonces. Uses GETDEL so
     * the read and delete are one atomic command: two concurrent requests with the same
     * correct code cannot both succeed.
     */
    public boolean consumeIfMatches(String key, String candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            String stored = redis.opsForValue().getAndDelete(namespaced(key));
            record(stored != null);
            return stored != null && MessageDigest.isEqual(
                    stored.getBytes(StandardCharsets.UTF_8),
                    candidate.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return failed("consume", key, e, false);
        }
    }

    /** Remaining time to live, or empty if the key is absent or has no expiry. */
    public Optional<Duration> timeToLive(String key) {
        try {
            Long seconds = redis.getExpire(namespaced(key));
            return (seconds == null || seconds < 0)
                    ? Optional.empty()
                    : Optional.of(Duration.ofSeconds(seconds));
        } catch (Exception e) {
            return failed("ttl", key, e, Optional.empty());
        }
    }

    // ==================================================================
    // Object values - JSON
    // ==================================================================

    public <T> Optional<T> get(String key, Class<T> type) {
        return read(key, json -> mapper.readValue(json, type));
    }

    /** Generic-typed read: {@code get(key, new TypeReference<List<Product>>() {})}. */
    public <T> Optional<T> get(String key, TypeReference<T> type) {
        return read(key, json -> mapper.readValue(json, type));
    }

    /** TTL is required: an entry written without one lives until Redis hits maxmemory. */
    public void put(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(namespaced(key), mapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            failed("write", key, e, null);
        }
    }

    public void put(String key, Object value) {
        put(key, value, properties.defaultTtl());
    }

    // ==================================================================
    // Read-through
    // ==================================================================

    /** Cached value, or load it, cache it and return it. A null loader result is not cached. */
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        return get(key, type).orElseGet(() -> loadAndStore(key, ttl, loader));
    }

    public <T> T getOrLoad(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        return get(key, type).orElseGet(() -> loadAndStore(key, ttl, loader));
    }

    /**
     * {@link #getOrLoad} with one loader at a time across all instances.
     *
     * <p>Without it, a hot key expiring sends every concurrent request to the database at
     * once. SET NX elects one loader; the rest wait briefly and re-read, then load anyway
     * rather than block indefinitely.
     */
    public <T> T getOrLoadSingleFlight(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }

        String lockKey = namespaced(key) + ":lock";
        if (acquireLock(lockKey)) {
            try {
                return loadAndStore(key, ttl, loader);
            } finally {
                deleteRaw(lockKey);
            }
        }

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loader.get();
        }
        return get(key, type).orElseGet(loader);
    }

    // ==================================================================
    // Eviction
    // ==================================================================

    public void evict(String key) {
        deleteRaw(namespaced(key));
    }

    /** Bulk evict by prefix. SCAN, never KEYS - KEYS is O(N) and blocks the server. */
    public void evictPrefix(String prefix) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(namespaced(prefix) + "*").count(SCAN_BATCH).build();

        try (Cursor<String> cursor = redis.scan(options)) {
            List<String> batch = new ArrayList<>(SCAN_BATCH);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= SCAN_BATCH) {
                    redis.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redis.delete(batch);
            }
        } catch (Exception e) {
            failed("prefix evict", prefix, e, null);
        }
    }

    // ==================================================================
    // Stats
    // ==================================================================

    public CacheStats stats() {
        return new CacheStats(hits.get(), misses.get(), errors.get());
    }

    public void resetStats() {
        hits.set(0);
        misses.set(0);
        errors.set(0);
    }

    public record CacheStats(long hits, long misses, long errors) {
        public double hitRatio() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }
    }

    // ==================================================================
    // Internals
    // ==================================================================

    private String namespaced(String key) {
        return properties.namespace() + ":" + key;
    }

    private <T> Optional<T> read(String key, JsonReader<T> reader) {
        try {
            String json = redis.opsForValue().get(namespaced(key));
            record(json != null);
            return json == null ? Optional.empty() : Optional.ofNullable(reader.read(json));
        } catch (Exception e) {
            // Covers an unreachable Redis and a value that no longer deserialises.
            // Both mean the same thing to the caller: treat it as a miss.
            return failed("read", key, e, Optional.empty());
        }
    }

    private <T> T loadAndStore(String key, Duration ttl, Supplier<T> loader) {
        T value = loader.get();
        if (value != null) {
            put(key, value, ttl);
        }
        return value;
    }

    private boolean acquireLock(String lockKey) {
        try {
            return Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(lockKey, "1", properties.lockTtl()));
        } catch (Exception e) {
            // Redis unreachable: load unlocked rather than fail.
            return failed("lock", lockKey, e, false);
        }
    }

    private void deleteRaw(String fullKey) {
        try {
            redis.delete(fullKey);
        } catch (Exception e) {
            failed("delete", fullKey, e, null);
        }
    }

    private void record(boolean hit) {
        (hit ? hits : misses).incrementAndGet();
    }

    /** Counts the error, logs it, and hands back the caller's degraded result. */
    private <T> T failed(String operation, String key, Exception e, T fallback) {
        errors.incrementAndGet();
        log.warn("Cache {} failed for key={} - degrading", operation, key, e);
        return fallback;
    }

    /** Lets both read() overloads share one try/catch despite Jackson's checked exceptions. */
    @FunctionalInterface
    private interface JsonReader<T> {
        T read(String json) throws Exception;
    }
}
