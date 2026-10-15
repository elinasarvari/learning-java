package com.example.app.repository.invoice.spec;

import com.example.app.repository.invoice.entity.InvoiceEntity;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Ordinary, optional user filters.
 *
 * <p>These follow the standard STD-BE-004 idiom: return {@code null} when the filter is
 * absent and let {@code Specification.allOf(...)} skip it. That is safe <i>here</i>
 * because a skipped user filter widens the result set, which is exactly what the user
 * asked for by leaving the field blank.
 *
 * <p>Security specifications must NOT do this - see
 * {@link com.example.app.repository.security.spec.SecuritySpecifications}.
 */
public final class InvoiceSpecifications {

    private InvoiceSpecifications() {}

    public static Specification<InvoiceEntity> numberContains(String term) {
        if (!StringUtils.hasText(term)) {
            return null;
        }
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("invoiceNumber")), "%" + term.toLowerCase() + "%");
    }

    public static Specification<InvoiceEntity> forCustomers(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("customerId").in(customerIds);
    }
}
