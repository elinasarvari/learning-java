package com.example.app.service.invoice.domain;

import java.util.List;

/**
 * The filters a <i>user</i> chose.
 *
 * <p>Security restrictions are deliberately not a field here. Mixing "what the user asked
 * to see" with "what the user is allowed to see" into one object is how the two end up
 * being treated the same way - optional, skippable, overridable from a request parameter.
 */
public record InvoiceCriteria(String term, List<Long> customerIds) {

    public static InvoiceCriteria empty() {
        return new InvoiceCriteria(null, List.of());
    }
}
