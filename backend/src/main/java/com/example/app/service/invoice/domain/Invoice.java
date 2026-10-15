package com.example.app.service.invoice.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Business-layer view of an invoice. Never an {@code @Entity}, never a response DTO. */
public record Invoice(
        String id,
        String invoiceNumber,
        Long customerId,
        String region,
        String status,
        BigDecimal amount,
        LocalDate issuedOn) {}
