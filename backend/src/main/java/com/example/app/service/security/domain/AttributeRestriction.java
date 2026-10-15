package com.example.app.service.security.domain;

import java.util.List;
import java.util.Objects;

/**
 * "This user may only see rows where {@code fieldName} is one of {@code allowedValues}."
 *
 * <p>{@code fieldName} is a JPA attribute name on the target entity, not a column name -
 * it is fed to {@code root.get(fieldName)} by
 * {@link com.example.app.repository.security.spec.SecuritySpecifications}.
 *
 * @param fieldName     JPA attribute name on the restricted entity, e.g. {@code "region"}
 * @param allowedValues the values the user is permitted to see; never empty
 */
public record AttributeRestriction(String fieldName, List<String> allowedValues) {

    public AttributeRestriction {
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(allowedValues, "allowedValues");
        if (allowedValues.isEmpty()) {
            // An empty allow-list is ambiguous: it reads as either "no values permitted"
            // or "restriction not configured". Refuse to construct one rather than let a
            // caller guess - see STD-SEC-005 on keeping those two states distinguishable.
            throw new IllegalArgumentException(
                    "allowedValues must not be empty for field '" + fieldName + "'");
        }
        allowedValues = List.copyOf(allowedValues);
    }
}
