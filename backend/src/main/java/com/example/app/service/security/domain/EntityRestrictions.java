package com.example.app.service.security.domain;

import java.util.List;
import java.util.Objects;

/**
 * Row-level access policy, already resolved, for one (user, account, entity) triple.
 *
 * <p>This is the value object that crosses the service -> repository boundary. It is the
 * reason the repository does not need a policy service injected into it (STD-SEC-001):
 * it carries the answer rather than the means of obtaining it, has no behaviour, and
 * depends on nothing.
 *
 * <p>It is deeply immutable, so the same instance is safely reused by every query in a
 * request and can be cached.
 */
public record EntityRestrictions(RestrictedEntity entity, List<AttributeRestriction> attributes) {

    public EntityRestrictions {
        Objects.requireNonNull(entity, "entity");
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /**
     * An explicit, resolved "this user has no restrictions on this entity".
     *
     * <p>Deliberately not the same thing as {@code null}. {@code none()} is a decision
     * that was reached; {@code null} is a decision that was never made. STD-SEC-005
     * depends on being able to tell the two apart - an error path must never be able to
     * produce this value by accident.
     */
    public static EntityRestrictions none(RestrictedEntity entity) {
        return new EntityRestrictions(entity, List.of());
    }

    public boolean isEmpty() {
        return attributes.isEmpty();
    }
}
