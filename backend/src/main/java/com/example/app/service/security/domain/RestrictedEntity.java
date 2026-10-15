package com.example.app.service.security.domain;

/**
 * Entity types that support per-user row-level restrictions.
 *
 * <p>Carried inside {@link EntityRestrictions} so a restriction set resolved for one
 * entity cannot be silently applied to a query over another (STD-SEC-002).
 */
public enum RestrictedEntity {
    INVOICE
}
