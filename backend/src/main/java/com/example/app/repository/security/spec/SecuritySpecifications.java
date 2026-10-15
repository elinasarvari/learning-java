package com.example.app.repository.security.spec;

import com.example.app.service.security.domain.AttributeRestriction;
import com.example.app.service.security.domain.EntityRestrictions;
import jakarta.persistence.criteria.Predicate;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates resolved policy into a {@code WHERE} fragment (STD-SEC-003).
 *
 * <p>Generic in {@code T} because the translation is entity-independent: it reads JPA
 * attribute names off {@link AttributeRestriction} and applies them to whatever root it
 * is given. The same factory serves every restrictable entity.
 *
 * <p>This class lives under {@code repository/} and imports from
 * {@code service/security/domain} - which is a domain value object, not a service, so
 * STD-SEC-001 is satisfied. The forbidden direction is a dependency on <i>behaviour</i>
 * in the layer above, not on a record that happens to be declared there.
 */
public final class SecuritySpecifications {

    private SecuritySpecifications() {}

    /**
     * Restricts a query to the rows the resolved policy permits.
     *
     * <p><b>Never returns {@code null}</b>, unlike the optional-filter specifications in
     * {@code InvoiceSpecifications}. {@code Specification.allOf(...)} silently drops
     * {@code null} elements, so a security factory that returned {@code null} would
     * silently disable access control and every positive test would still pass. An empty
     * policy produces an explicit always-true predicate instead, and a {@code null}
     * policy is rejected outright: a bug then surfaces as a broken query or an NPE at the
     * call site rather than as a data leak.
     */
    public static <T> Specification<T> restrictedBy(EntityRestrictions restrictions) {
        Objects.requireNonNull(
                restrictions,
                "restrictions must be resolved by the service layer before reaching the data layer; "
                        + "use EntityRestrictions.none(entity) to express 'no restrictions apply'");

        return (root, query, cb) -> {
            if (restrictions.isEmpty()) {
                return cb.conjunction();
            }
            Predicate[] predicates = restrictions.attributes().stream()
                    .map(attr -> toPredicate(root.get(attr.fieldName()), attr))
                    .toArray(Predicate[]::new);

            // AND across fields, IN within a field: "region IN (OH) AND status IN (PAID)".
            return cb.and(predicates);
        };
    }

    private static Predicate toPredicate(
            jakarta.persistence.criteria.Path<Object> path, AttributeRestriction attr) {
        // .in(Collection) binds parameters; the values never reach the SQL as text.
        return path.in(attr.allowedValues());
    }
}
