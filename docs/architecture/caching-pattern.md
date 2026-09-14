# Caching Pattern

**Repo**: `backend/` | **Stack**: Spring Boot 3.3.2 / Spring Data Redis (Lettuce) / Redis 7 | **Status**: Implementation-validated

Redis caching built directly on `StringRedisTemplate`, deliberately **without** Spring's `@Cacheable` abstraction.

---

## Technical Context

| Item | Value | Status |
|------|-------|--------|
| **Client** | Lettuce via `spring-boot-starter-data-redis` | ✅ |
| **Pooling** | `commons-pool2`, 16 active / 8 idle | ✅ |
| **Abstraction** | None — `RedisCache` facade over `StringRedisTemplate` | ✅ |
| **Serialization** | Plain JSON strings via a `copy()` of Boot's `ObjectMapper` | ✅ |
| **Default TTL** | 5 minutes (`app.cache.default-ttl`) | ✅ |
| **Key namespace** | `learning-java:` (`app.cache.namespace`) | ✅ |
| **Stampede control** | `SET NX` single-flight lock | ✅ |
| **Failure mode** | Degrade to source; never propagate | ✅ |
| **Metrics** | In-process hit/miss/error counters | ✅ basic |
| **Distributed invalidation** | — | ❌ GAP-CACHE-001 |

---

## Why not `@Cacheable`

`spring-boot-starter-cache` is **not** on the classpath. The trade-off:

| | `@Cacheable` | `RedisCache` (this repo) |
|---|---|---|
| Call-site cost | One annotation | Explicit, or one `getOrLoad` line |
| Self-invocation | **Silently skipped** (proxy-based) | Works |
| TTL granularity | Per cache, fixed at config time | Per call |
| Redis data structures | Not reachable | Full `StringRedisTemplate` available |
| Failure behaviour | Propagates unless a `CacheErrorHandler` is added | Degrades by default |

The self-invocation trap is the decisive one: with `@Cacheable`, a call to `this.cachedMethod()` from inside the same bean bypasses the proxy and the cache, with no error and no log line.

---

## Structure

```text
backend/src/main/java/com/example/app/
├── cache/                                   # CROSS-CUTTING INFRASTRUCTURE
│   ├── RedisCache.java                      #   the only class that talks to Redis
│   ├── CacheKeys.java                       #   every key shape, in one place
│   └── CacheProperties.java                 #   @ConfigurationProperties("app.cache")
├── config/
│   ├── RedisCacheConfig.java                #   @EnableConfigurationProperties
│   └── CatalogSeedData.java                 #   H2 seed rows for the example
├── repository/catalog/                      # DATA ACCESS LAYER
│   ├── ProductRepository.java
│   └── entity/ProductEntity.java
├── service/catalog/                         # BUSINESS LAYER — caching lives here
│   ├── CatalogService.java                  #   interface: no mention of caching
│   ├── CatalogServiceImpl.java              #   read-through + evict-on-write
│   └── domain/Product.java                  #   the record that gets cached
└── web/catalog/                             # PRESENTATION LAYER
    ├── CatalogController.java
    └── dto/{ProductResponse,UpdatePriceRequest}.java
```

`cache/` sits alongside `camel/` as a cross-cutting package: it does not nest inside the three-layer stack, and any layer may depend on it.

---

## Normative Rules

### STD-CACHE-001 — Caching belongs to the service layer

Caching MUST be applied in `service/<feature>/XxxServiceImpl`. Controllers MUST NOT call `RedisCache` for business data, and repositories MUST NOT be cache-aware.

**Why**: the cache boundary has to sit where a "unit of business result" is defined. Cache at the controller and the same data is re-cached per endpoint shape; cache at the repository and you cache rows, not answers.

*Exception*: `CatalogController` injects `RedisCache` solely for the `/cache/stats` diagnostic endpoint — never for product data.

### STD-CACHE-002 — Cache domain records, never entities

The value passed to `RedisCache` MUST be a `service.<feature>.domain` record. `@Entity` types MUST NOT be cached.

**Why**: a JPA entity carries lazy proxies and a persistence-context reference. Serialising one either drags in unintended relations or throws `LazyInitializationException` after the session closes. Caching the domain record also decouples cached data from the database schema.

### STD-CACHE-003 — Every write sets a TTL

`RedisCache.put` requires a `Duration`. An unbounded entry MUST NOT be written.

**Why**: without a TTL an entry lives until Redis reaches `maxmemory` and begins evicting keys by policy rather than by intent.

### STD-CACHE-004 — Keys are built by `CacheKeys`

Key strings MUST NOT be inlined at call sites.

**Why**: the reader and the writer that invalidates it must agree. A writer evicting `product:42` while the reader stored `products:42` leaves stale data for the full TTL, with nothing failing.

### STD-CACHE-005 — A write invalidates every entry it affects

An update MUST evict the entity's own key **and** every aggregate (list, count, search result) that can contain it.

**Why**: this is the most common cache bug in practice. `CatalogServiceImpl.updatePrice` evicts `product:{id}` and then `evictPrefix("product:category:")`, because the product appears inside cached category lists too.

### STD-CACHE-006 — Evict after the write, inside the transaction boundary

`cache.evict(...)` MUST follow `repository.save(...)`.

**Why**: evicting first opens a window in which a concurrent reader repopulates the cache from the pre-write row, and that stale value then survives the full TTL.

**Known gap (GAP-CACHE-001)**: a reader can still load the old row and write it back between `save()` and `evict()`. Closing that requires write-through or versioned keys.

### STD-CACHE-007 — Cache failures degrade, never propagate

Every `RedisCache` operation catches `Exception`, logs at WARN, and continues. Callers MUST NOT see a Redis failure as an error.

**Why**: a cache is an optimisation. A Redis outage should make the application slower, not break it. *Verified*: with Redis stopped, all catalog endpoints return 200 and the error counter increments.

### STD-CACHE-008 — SCAN, never KEYS

Bulk eviction MUST use `SCAN` with a bounded batch.

**Why**: `KEYS` is O(N) over the entire keyspace and blocks Redis's single command thread. Harmless against 100 keys in dev; an outage against a million in production.

### STD-CACHE-009 — Namespace every key

All keys carry the `app.cache.namespace` prefix, applied centrally in `RedisCache`.

**Why**: prevents collisions and, more importantly, stops `evictPrefix` from deleting another service's keys on a shared Redis instance.

---

## The Three Patterns

### 1. Read-through, single object

```java
Product product = cache.getOrLoadSingleFlight(
        CacheKeys.product(id), Product.class, ttl,
        () -> loadFromDatabase(id));
```

`getOrLoadSingleFlight` over plain `getOrLoad` for hot keys: when a hot key expires, every concurrent request misses simultaneously and hits the database together. `SET NX` elects one loader; the rest wait 50ms and re-read.

### 2. Read-through, generic type

```java
private static final TypeReference<List<Product>> PRODUCT_LIST = new TypeReference<>() {};

return cache.getOrLoad(
        CacheKeys.productsByCategory(category), PRODUCT_LIST, ttl,
        () -> loadCategoryFromDatabase(category));
```

Erasure means `List<Product>.class` cannot be written. The `TypeReference` is a constant, not an allocation per call.

### 3. Evict on write

```java
ProductEntity saved = repository.save(entity);
cache.evict(CacheKeys.product(id));              // the entity itself
cache.evictPrefix(CacheKeys.allCategoryLists()); // every list containing it
return toDomain(saved);
```

---

## Serialization

Values are stored as **plain JSON strings**, not with `GenericJackson2JsonRedisSerializer`.

That serializer embeds the fully-qualified Java class name in every entry, so renaming or moving a cached class breaks every entry already in Redis until the TTL drains. Plain JSON with the type supplied at read time avoids this, keeps entries readable in `redis-cli`, and makes generic types work.

`RedisCache` takes a `copy()` of Boot's auto-configured `ObjectMapper` and disables `FAIL_ON_UNKNOWN_PROPERTIES` on the copy:

- **`copy()`** — that mapper also serialises every HTTP response; mutating it would change the API wire format as a side effect.
- **No `ObjectMapper` `@Bean`** — Boot's is `@ConditionalOnMissingBean`, so declaring one would *replace* the application-wide mapper.
- **`FAIL_ON_UNKNOWN_PROPERTIES=false`** — cached JSON outlives deploys. After a field is removed from a record, entries written by the previous version still carry it.

---

## Known Gaps

| ID | Gap | Impact |
|----|-----|--------|
| **GAP-CACHE-001** | Read-repopulate race between `save()` and `evict()` | Stale entry for up to one TTL under concurrent read+write |
| **GAP-CACHE-002** | No negative caching | Repeated requests for a non-existent id reach the database every time (cache penetration) |
| **GAP-CACHE-003** | No circuit breaker | With Redis down, every call pays the 2s connect timeout before degrading |
| **GAP-CACHE-004** | Counters are in-process, not Micrometer | Hit ratio is per-instance and resets on restart |
| **GAP-CACHE-005** | No Testcontainers integration test | Caching logic is unit-tested against a mock; no test exercises a real Redis |

---

## Related

- `docs/architecture/layer-design.md` — STD-LAY-001/002, layer boundaries
- `docs/architecture/backend-patterns.md` — project structure
- `backend/src/test/java/com/example/app/cache/RedisCacheTest.java` — behaviour spec
