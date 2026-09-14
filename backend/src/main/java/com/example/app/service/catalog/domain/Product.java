package com.example.app.service.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain representation of a product, and the type that actually gets cached.
 *
 * <p>Caching the domain record rather than {@code ProductEntity} matters for two reasons:
 *
 * <ul>
 *   <li>A JPA entity can hold lazy proxies and a reference to its persistence context.
 *       Serialising one either drags in unwanted relations or throws
 *       {@code LazyInitializationException} once the session has closed.
 *   <li>The database schema is free to change without invalidating entries already in
 *       Redis, because the cached shape is this record, not the table.
 * </ul>
 *
 * <p>Records serialise cleanly with Jackson in Spring Boot 3 - no annotations or custom
 * module needed - as long as the module for {@code java.time} is registered, which
 * {@code spring-boot-starter-web} does by default.
 */
public record Product(
        String id,
        String name,
        String category,
        BigDecimal price,
        Instant updatedAt) {
}
