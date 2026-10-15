package com.example.app.repository.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The entity the row-level security example filters.
 *
 * <p>{@code region} and {@code status} are the restrictable attributes - see the seeded
 * policy in {@link com.example.app.config.InvoiceSeedData}.
 */
@Entity
@Table(
        name = "invoice",
        indexes = {
            @Index(name = "ix_invoice_customer_id", columnList = "customer_id"),
            @Index(name = "ix_invoice_region", columnList = "region")
        })
public class InvoiceEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 32)
    private String id;

    @Column(name = "invoice_number", nullable = false, length = 32)
    private String invoiceNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** Restrictable attribute. Two-letter state code. */
    @Column(nullable = false, length = 2)
    private String region;

    /** Restrictable attribute. {@code OPEN} | {@code PAID} | {@code VOID}. */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    protected InvoiceEntity() {
        // required by JPA
    }

    public InvoiceEntity(
            String id,
            String invoiceNumber,
            Long customerId,
            String region,
            String status,
            BigDecimal amount,
            LocalDate issuedOn) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.customerId = customerId;
        this.region = region;
        this.status = status;
        this.amount = amount;
        this.issuedOn = issuedOn;
    }

    public String getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getRegion() {
        return region;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }
}
