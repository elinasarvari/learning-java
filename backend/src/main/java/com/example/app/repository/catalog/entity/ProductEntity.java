package com.example.app.repository.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for a catalog product - the "slow" source of truth the cache sits in front of.
 *
 * <p>Per STD-LAY-001 this type stays inside the data access layer: nothing under
 * {@code web/} may reference it, and it is never what gets cached. The cache stores the
 * {@code Product} domain record instead, so a schema change to this entity cannot
 * invalidate entries already in Redis.
 */
@Entity
@Table(name = "product")
public class ProductEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductEntity() {
        // required by JPA
    }

    public ProductEntity(String id, String name, String category, BigDecimal price, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
