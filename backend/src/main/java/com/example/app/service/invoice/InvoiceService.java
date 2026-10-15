package com.example.app.service.invoice;

import com.example.app.service.invoice.domain.Invoice;
import com.example.app.service.invoice.domain.InvoiceCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InvoiceService {

    /**
     * Returns the page of invoices matching {@code criteria} that {@code userId} is
     * permitted to see on {@code accountId}.
     *
     * <p>The returned {@link Page#getTotalElements()} counts only permitted rows,
     * because the same specification drives both queries.
     */
    Page<Invoice> search(InvoiceCriteria criteria, Long userId, Long accountId, Pageable pageable);
}
