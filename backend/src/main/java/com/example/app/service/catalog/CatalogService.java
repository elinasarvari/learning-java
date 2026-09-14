package com.example.app.service.catalog;

import com.example.app.service.catalog.domain.Product;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Catalog reads and writes, cached for 5 minutes.
 *
 * <p>Per STD-LAY-002 the presentation layer depends on this interface only. Caching is an
 * implementation detail of {@link CatalogServiceImpl} and is deliberately absent from
 * this contract - callers must not have to know whether a result was cached, and swapping
 * Redis out must not change this file.
 */
public interface CatalogService {

    Optional<Product> findById(String id);

    List<Product> findByCategory(String category);

    /** Updates the price and invalidates every cache entry affected by the change. */
    Product updatePrice(String id, BigDecimal newPrice);
}
