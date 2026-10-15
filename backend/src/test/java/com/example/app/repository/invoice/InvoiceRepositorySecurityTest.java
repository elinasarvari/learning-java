package com.example.app.repository.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.app.repository.invoice.entity.InvoiceEntity;
import com.example.app.repository.security.spec.SecuritySpecifications;
import com.example.app.service.security.domain.AttributeRestriction;
import com.example.app.service.security.domain.EntityRestrictions;
import com.example.app.service.security.domain.RestrictedEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The payoff for STD-SEC-001: this is a plain {@code @DataJpaTest} with no mocks and no
 * service graph. That is only possible because {@code InvoiceRepository} has no
 * dependency on {@code SecurityPolicyService} - inject the service into the repository
 * and this test needs the whole application context.
 */
@DataJpaTest
@ActiveProfiles("test")
class InvoiceRepositorySecurityTest {

    @Autowired
    private InvoiceRepository repository;

    @BeforeEach
    void seed() {
        LocalDate day = LocalDate.of(2026, 1, 15);
        repository.saveAll(List.of(
                new InvoiceEntity("inv-001", "INV-1001", 100L, "OH", "OPEN", new BigDecimal("1200.00"), day),
                new InvoiceEntity("inv-002", "INV-1002", 100L, "OH", "PAID", new BigDecimal("845.50"), day),
                new InvoiceEntity("inv-003", "INV-1003", 100L, "MI", "PAID", new BigDecimal("2310.00"), day),
                new InvoiceEntity("inv-004", "INV-1004", 100L, "MI", "OPEN", new BigDecimal("980.25"), day),
                new InvoiceEntity("inv-005", "INV-1005", 100L, "IN", "PAID", new BigDecimal("450.00"), day),
                new InvoiceEntity("inv-006", "INV-1006", 100L, "IN", "VOID", new BigDecimal("175.75"), day)));
    }

    @Test
    @DisplayName("a single-field restriction returns only rows with a permitted value")
    void findAll_should_return_only_rows_matching_a_single_restriction() {
        Page<InvoiceEntity> page = repository.findAll(
                SecuritySpecifications.restrictedBy(restrictions(
                        new AttributeRestriction("region", List.of("OH")))),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(InvoiceEntity::getRegion).containsOnly("OH");
        // The NEGATIVE assertion is the one that matters: a filter that was silently
        // dropped still satisfies every "the permitted rows are present" assertion.
        assertThat(page.getContent()).extracting(InvoiceEntity::getId).doesNotContain("inv-003", "inv-005");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("multiple fields are ANDed, values within a field are ORed")
    void findAll_should_and_across_fields_and_or_within_a_field() {
        Page<InvoiceEntity> page = repository.findAll(
                SecuritySpecifications.restrictedBy(restrictions(
                        new AttributeRestriction("region", List.of("OH", "MI")),
                        new AttributeRestriction("status", List.of("PAID")))),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(InvoiceEntity::getId)
                .containsExactlyInAnyOrder("inv-002", "inv-003");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("the count query is filtered too, not just the rows query")
    void findAll_should_filter_the_count_query_as_well_as_the_page() {
        // Page size 1 forces the count to come from the separate count query rather than
        // from the size of the returned list - which is exactly where a hand-written
        // countXxx() that forgot the security predicate would report 6.
        Page<InvoiceEntity> page = repository.findAll(
                SecuritySpecifications.restrictedBy(restrictions(
                        new AttributeRestriction("region", List.of("OH")))),
                PageRequest.of(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("an empty restriction set returns everything")
    void findAll_should_return_all_rows_when_no_restrictions_apply() {
        Page<InvoiceEntity> page = repository.findAll(
                SecuritySpecifications.restrictedBy(EntityRestrictions.none(RestrictedEntity.INVOICE)),
                PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(6);
    }

    @Test
    @DisplayName("null restrictions are rejected rather than treated as unrestricted")
    void restrictedBy_should_reject_null_restrictions() {
        assertThatThrownBy(() -> SecuritySpecifications.restrictedBy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("EntityRestrictions.none");
    }

    private static EntityRestrictions restrictions(AttributeRestriction... attributes) {
        return new EntityRestrictions(RestrictedEntity.INVOICE, List.of(attributes));
    }
}
