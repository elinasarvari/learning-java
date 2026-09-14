package com.example.app.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.app.cache.CacheProperties;
import com.example.app.cache.RedisCache;
import com.example.app.repository.catalog.ProductRepository;
import com.example.app.repository.catalog.entity.ProductEntity;
import com.example.app.service.catalog.domain.Product;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the caching <i>contract</i> of {@link CatalogServiceImpl} - that reads go through
 * the cache and that a write invalidates everything it should. {@link RedisCache} is
 * mocked, so no Redis is required.
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceImplTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private RedisCache cache;

    private CatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CatalogServiceImpl(
                repository,
                cache,
                new CacheProperties("test", Duration.ofMinutes(5), Duration.ofSeconds(10)),
                0L); // no simulated latency in tests
    }

    @Test
    @DisplayName("findById reads through the cache with a 5-minute TTL")
    void findByIdUsesCacheWithFiveMinuteTtl() {
        Product cached = new Product("p-001", "Keyboard", "peripherals",
                new BigDecimal("129.00"), Instant.EPOCH);
        when(cache.getOrLoadSingleFlight(eq("product:p-001"), eq(Product.class),
                eq(Duration.ofMinutes(5)), any()))
                .thenReturn(cached);

        assertThat(service.findById("p-001")).contains(cached);
    }

    @Test
    @DisplayName("findById returns empty when the loader finds nothing")
    void findByIdReturnsEmptyWhenAbsent() {
        when(cache.getOrLoadSingleFlight(anyString(), eq(Product.class), any(), any()))
                .thenReturn(null);

        assertThat(service.findById("nope")).isEmpty();
    }

    @Test
    @DisplayName("the loader hits the repository only when the cache misses")
    void loaderQueriesRepositoryOnMiss() {
        when(repository.findById("p-001")).thenReturn(Optional.of(entity("129.00")));

        // Simulate a miss by having the mock invoke the supplier it was handed.
        when(cache.getOrLoadSingleFlight(anyString(), eq(Product.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(3, Supplier.class).get());

        assertThat(service.findById("p-001"))
                .map(Product::price)
                .contains(new BigDecimal("129.00"));
        verify(repository).findById("p-001");
    }

    @Test
    @DisplayName("updatePrice evicts both the product entry and every category list")
    void updatePriceEvictsProductAndCategoryLists() {
        ProductEntity entity = entity("129.00");
        when(repository.findById("p-001")).thenReturn(Optional.of(entity));
        when(repository.save(any(ProductEntity.class))).thenAnswer(i -> i.getArgument(0));

        Product updated = service.updatePrice("p-001", new BigDecimal("99.00"));

        assertThat(updated.price()).isEqualByComparingTo("99.00");
        verify(cache).evict("product:p-001");
        // The forgettable half: the product also lives inside cached category lists.
        verify(cache).evictPrefix("product:category:");
    }

    private static ProductEntity entity(String price) {
        return new ProductEntity("p-001", "Keyboard", "peripherals",
                new BigDecimal(price), Instant.EPOCH);
    }
}
