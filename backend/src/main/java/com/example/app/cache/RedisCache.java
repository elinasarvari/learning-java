package com.example.app.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * A Redis-backed cache built directly on {@link StringRedisTemplate}, deliberately
 * WITHOUT Spring's {@code @Cacheable} abstraction.
 *
 * <p>Trade-offs of doing it this way:
 *
 * <ul>
 *   <li><b>Self-invocation works.</b> {@code @Cacheable} is proxy-based, so a call to
 *       {@code this.someCachedMethod()} from inside the same bean silently skips the
 *       cache. There is no proxy here, so caching happens wherever the call comes from.
 *   <li><b>TTL is per call, not per cache.</b> {@code @Cacheable} fixes TTL at
 *       configuration time; here the caller decides.
 *   <li><b>Cost:</b> the read/load/write dance is explicit at each call site. That is
 *       what {@link #getOrLoad} exists to collapse back into one line.
 * </ul>
 *
 * <p><b>Values are stored as plain JSON strings.</b> The obvious alternative,
 * {@code GenericJackson2JsonRedisSerializer}, embeds the fully-qualified Java class name
 * in every entry - which means renaming or moving a cached class breaks every entry
 * already in Redis until the TTL drains. Storing plain JSON and passing the target type
 * at read time avoids that, keeps entries readable in {@code redis-cli}, and makes
 * generic types such as {@code List<Product>} work properly.
 *
 * <p><b>Every operation degrades rather than throws.</b> A cache is an optimisation; a
 * Redis outage should make the application slower, not break it. Reads return empty on
 * failure and writes are dropped, both with a warning.
 */
@Component
public class RedisCache {

    private static final Logger log = LoggerFactory.getLogger(RedisCache.class);

    /** Scan/delete batch size for {@link #evictPrefix} - large enough to be cheap, small enough not to stall Redis. */
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

        // copy() rather than using Spring's ObjectMapper directly: that instance also
        // serialises every HTTP response, and mutating it here would change the API's
        // wire format as a side effect.
        //
        // FAIL_ON_UNKNOWN_PROPERTIES is disabled because cached JSON outlives deploys.
        // After a field is removed from a record, entries written by the previous
        // version still carry it; without this, every one of them throws on read until
        // the TTL drains.
        this.mapper = objectMapper.copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    /** Reads a cached value of a simple (non-generic) type. */
    public <T> Optional<T> get(String key, Class<T> type) {
        return read(key, json -> mapper.readValue(json, type));
    }

    /**
     * Reads a cached value of a generic type.
     *
     * <p>Needed because erasure means {@code List<Product>.class} cannot be expressed:
     * {@code cache.get(key, new TypeReference<List<Product>>() {})}.
     */
    public <T> Optional<T> get(String key, TypeReference<T> type) {
        return read(key, json -> mapper.readValue(json, type));
    }

    private <T> Optional<T> read(String key, JsonReader<T> reader) {
        try {
            String json = redis.opsForValue().get(namespaced(key));
            if (json == null) {
                misses.incrementAndGet();
                return Optional.empty();
            }
            hits.incrementAndGet();
            return Optional.ofNullable(reader.read(json));
        } catch (Exception e) {
            // Covers both Redis being unreachable and a value that no longer deserialises.
            // Either way the correct move is the same: behave as a miss and let the
            // caller load from source.
            errors.incrementAndGet();
            log.warn("Cache read failed for key={} - treating as miss", key, e);
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Write / evict
    // ------------------------------------------------------------------

    /**
     * Stores a value with an explicit TTL.
     *
     * <p>TTL is a required parameter on purpose. An entry written without one lives until
     * Redis hits {@code maxmemory} and starts evicting - at which point it evicts
     * whatever it likes, not what you expected.
     */
    public void put(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(namespaced(key), mapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            errors.incrementAndGet();
            log.warn("Cache write failed for key={} - value not cached", key, e);
        }
    }

    /** Stores a value using {@code app.cache.default-ttl} (5 minutes unless overridden). */
    public void put(String key, Object value) {
        put(key, value, properties.defaultTtl());
    }

    public void evict(String key) {
        try {
            redis.delete(namespaced(key));
        } catch (Exception e) {
            errors.incrementAndGet();
            log.warn("Cache evict failed for key={} - entry may serve stale until TTL", key, e);
        }
    }

    /**
     * Evicts every key starting with the given prefix.
     *
     * <p>Uses SCAN, never {@code KEYS}. {@code KEYS} is O(N) over the whole keyspace and
     * blocks Redis's single command thread for the duration - fine against 100 keys in
     * dev, an outage against a million in production. SCAN walks the keyspace in
     * bounded chunks instead.
     */
    public void evictPrefix(String prefix) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(namespaced(prefix) + "*")
                .count(SCAN_BATCH)
                .build();

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
            errors.incrementAndGet();
            log.warn("Cache prefix evict failed for prefix={}", prefix, e);
        }
    }

    // ------------------------------------------------------------------
    // Read-through
    // ------------------------------------------------------------------

    /**
     * Returns the cached value, or invokes {@code loader}, caches its result and returns it.
     *
     * <p>This is the method most call sites should use. A {@code null} from the loader is
     * returned but NOT cached - see the class-level note on cache penetration in
     * {@code docs/architecture/caching-pattern.md}.
     */
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        T value = loader.get();
        if (value != null) {
            put(key, value, ttl);
        }
        return value;
    }

    /** Generic-typed {@link #getOrLoad}, e.g. for a {@code List<Product>}. */
    public <T> T getOrLoad(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        T value = loader.get();
        if (value != null) {
            put(key, value, ttl);
        }
        return value;
    }

    /**
     * {@link #getOrLoad} with stampede protection across application instances.
     *
     * <p>Without this, the moment a hot key expires every concurrent request misses at
     * once and they all hit the database together - the load spike lands exactly when
     * the cache was supposed to be helping.
     *
     * <p>{@code SET NX} is atomic, so exactly one caller wins the lock and loads. The
     * others wait briefly and re-read. If the winner is slower than {@code retryWait}
     * they fall through and load too: a duplicated query is strictly better than
     * blocking the request thread indefinitely on a loader that may never finish.
     */
    public <T> T getOrLoadSingleFlight(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }

        String lockKey = namespaced(key) + ":lock";
        boolean acquired = false;
        try {
            acquired = Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(lockKey, "1", properties.lockTtl()));
        } catch (Exception e) {
            // Redis unreachable: fall through and load unlocked rather than fail.
            errors.incrementAndGet();
            log.warn("Could not acquire cache lock for key={} - loading unlocked", key, e);
        }

        if (acquired) {
            try {
                T value = loader.get();
                if (value != null) {
                    put(key, value, ttl);
                }
                return value;
            } finally {
                evictRaw(lockKey);
            }
        }

        // Lost the race - give the winner a moment, then re-check.
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loader.get();
        }
        return get(key, type).orElseGet(loader);
    }

    // ------------------------------------------------------------------
    // Introspection
    // ------------------------------------------------------------------

    /** Hit/miss/error counters, exposed by the catalog demo endpoint. */
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

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private String namespaced(String key) {
        return properties.namespace() + ":" + key;
    }

    /** Delete for a key that is already namespaced (the lock key). */
    private void evictRaw(String fullKey) {
        try {
            redis.delete(fullKey);
        } catch (Exception e) {
            // The lock carries its own TTL, so a failure here self-heals.
            log.warn("Failed to release cache lock {}", fullKey, e);
        }
    }

    /** Lets both read() overloads share one try/catch despite Jackson's checked exceptions. */
    @FunctionalInterface
    private interface JsonReader<T> {
        T read(String json) throws Exception;
    }
}
