package com.example.app.web.invoice.dto;

import com.example.app.service.invoice.domain.Invoice;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceResponse(
        String id,
        String invoiceNumber,
        Long customerId,
        String region,
        String status,
        BigDecimal amount,
        LocalDate issuedOn) {

    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.id(),
                invoice.invoiceNumber(),
                invoice.customerId(),
                invoice.region(),
                invoice.status(),
                invoice.amount(),
                invoice.issuedOn());
    }
}
