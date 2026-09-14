package com.example.app.service.catalog;

import com.example.app.cache.CacheKeys;
import com.example.app.cache.CacheProperties;
import com.example.app.cache.RedisCache;
import com.example.app.repository.catalog.ProductRepository;
import com.example.app.repository.catalog.entity.ProductEntity;
import com.example.app.service.catalog.domain.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worked example of caching with {@link RedisCache}: read-through on reads,
 * evict-on-write, 5-minute TTL.
 *
 * <p>Copy the three patterns below into your own service:
 *
 * <ol>
 *   <li>{@link #findById} - read-through for a single object.
 *   <li>{@link #findByCategory} - read-through for a generic type ({@code List<Product>}).
 *   <li>{@link #updatePrice} - write, then invalidate <i>everything</i> the write affects.
 * </ol>
 */
@Service
public class CatalogServiceImpl implements CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogServiceImpl.class);

    /**
     * Erasure means {@code List<Product>.class} cannot be written, so the generic type is
     * captured once here as a constant rather than allocating an anonymous subclass on
     * every call.
     */
    private static final TypeReference<List<Product>> PRODUCT_LIST = new TypeReference<>() {};

    private final ProductRepository repository;
    private final RedisCache cache;
    private final Duration ttl;
    private final long simulatedLatencyMs;

    public CatalogServiceImpl(
            ProductRepository repository,
            RedisCache cache,
            CacheProperties cacheProperties,
            @Value("${app.catalog.simulated-latency-ms:400}") long simulatedLatencyMs) {
        this.repository = repository;
        this.cache = cache;
        // 5 minutes, from app.cache.default-ttl. Read from configuration rather than
        // hardcoded so the window can be tuned per environment without a recompile.
        this.ttl = cacheProperties.defaultTtl();
        this.simulatedLatencyMs = simulatedLatencyMs;
    }

    // ------------------------------------------------------------------
    // Pattern 1: read-through, single object
    // ------------------------------------------------------------------

    @Override
    public Optional<Product> findById(String id) {
        // getOrLoadSingleFlight rather than getOrLoad: a product id is a hot key, and
        // when a hot key expires every concurrent request would otherwise miss together
        // and hit the database at the same moment.
        Product product = cache.getOrLoadSingleFlight(
                CacheKeys.product(id),
                Product.class,
                ttl,
                () -> loadFromDatabase(id));

        return Optional.ofNullable(product);
    }

    // ------------------------------------------------------------------
    // Pattern 2: read-through, generic type
    // ------------------------------------------------------------------

    @Override
    public List<Product> findByCategory(String category) {
        return cache.getOrLoad(
                CacheKeys.productsByCategory(category),
                PRODUCT_LIST,
                ttl,
                () -> loadCategoryFromDatabase(category));
    }

    // ------------------------------------------------------------------
    // Pattern 3: write, then invalidate
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Product updatePrice(String id, BigDecimal newPrice) {
        ProductEntity entity = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No product with id " + id));

        entity.setPrice(newPrice);
        entity.setUpdatedAt(Instant.now());
        ProductEntity saved = repository.save(entity);

        // Evict AFTER the write, never before: evicting first opens a window in which a
        // concurrent reader re-populates the cache from the pre-write row and that stale
        // value then survives for the full TTL.
        //
        // Note this is still not airtight - a reader can load the old row and write it
        // back between save() and evict(). Closing that properly needs write-through or
        // versioned keys; see docs/architecture/caching-pattern.md.
        cache.evict(CacheKeys.product(id));

        // The single-product entry is not the only thing invalidated by this write: the
        // product also appears inside cached category lists. Forgetting this second
        // eviction is the most common cache bug - the detail endpoint shows the new
        // price while the list endpoint shows the old one for up to 5 minutes.
        cache.evictPrefix(CacheKeys.allCategoryLists());

        return toDomain(saved);
    }

    // ------------------------------------------------------------------
    // Loaders - only reached on a cache miss
    // ------------------------------------------------------------------

    private Product loadFromDatabase(String id) {
        simulateSlowQuery();
        log.info("CACHE MISS - loading product {} from database", id);
        return repository.findById(id).map(CatalogServiceImpl::toDomain).orElse(null);
        // Returning null means "not found". RedisCache does not cache nulls, so repeated
        // requests for a non-existent id reach the database every time (cache
        // penetration). Acceptable here; for a public endpoint, cache a short-lived
        // negative marker instead.
    }

    private List<Product> loadCategoryFromDatabase(String category) {
        simulateSlowQuery();
        log.info("CACHE MISS - loading category {} from database", category);
        return repository.findByCategoryOrderByNameAsc(category).stream()
                .map(CatalogServiceImpl::toDomain)
                .toList();
    }

    /**
     * Stands in for a genuinely expensive query so the cache's effect is observable
     * against H2, which is otherwise too fast to measure. Same intent as
     * {@code FlakyService} in the Camel examples. Set
     * {@code app.catalog.simulated-latency-ms=0} to disable.
     */
    private void simulateSlowQuery() {
        if (simulatedLatencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(simulatedLatencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Product toDomain(ProductEntity entity) {
        return new Product(
                entity.getId(),
                entity.getName(),
                entity.getCategory(),
                entity.getPrice(),
                entity.getUpdatedAt());
    }
}
