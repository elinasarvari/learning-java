package com.example.app.web.catalog.dto;

import com.example.app.service.catalog.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * HTTP response shape for a product.
 *
 * <p>Separate from the {@code Product} domain record on purpose (STD-LAY-001): the wire
 * contract and the cached shape evolve independently. Adding a field here is a harmless
 * API change; adding one to {@code Product} changes what sits in Redis.
 */
public record ProductResponse(
        String id,
        String name,
        String category,
        BigDecimal price,
        Instant updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.id(),
                product.name(),
                product.category(),
                product.price(),
                product.updatedAt());
    }
}
