package com.example.app.service.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.app.repository.invoice.InvoiceRepository;
import com.example.app.repository.invoice.entity.InvoiceEntity;
import com.example.app.service.invoice.domain.Invoice;
import com.example.app.service.invoice.domain.InvoiceCriteria;
import com.example.app.service.security.AccessPolicyUnavailableException;
import com.example.app.service.security.SecurityPolicyService;
import com.example.app.service.security.domain.AttributeRestriction;
import com.example.app.service.security.domain.EntityRestrictions;
import com.example.app.service.security.domain.RestrictedEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

/**
 * Tests the <i>composition</i> contract - that policy is resolved once, in this layer,
 * and that a failure to resolve it stops the query from running at all.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private SecurityPolicyService securityPolicyService;

    private InvoiceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InvoiceServiceImpl(invoiceRepository, securityPolicyService);
    }

    @Test
    @DisplayName("resolves policy exactly once per search, not once per query")
    void search_should_resolve_policy_once() {
        when(securityPolicyService.resolve(1L, 100L, RestrictedEntity.INVOICE))
                .thenReturn(new EntityRestrictions(
                        RestrictedEntity.INVOICE,
                        List.of(new AttributeRestriction("region", List.of("OH")))));
        when(invoiceRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(invoice())));

        Page<Invoice> result =
                service.search(InvoiceCriteria.empty(), 1L, 100L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        // findAll(spec, pageable) internally issues the rows query and the count query,
        // so one resolve() call covers both. A separate countInvoices(...) method would
        // make this 2 - and give the predicate two places to drift out of sync.
        verify(securityPolicyService, times(1)).resolve(1L, 100L, RestrictedEntity.INVOICE);
    }

    @Test
    @DisplayName("does not query invoices at all when policy cannot be resolved")
    void search_should_not_query_when_policy_resolution_fails() {
        when(securityPolicyService.resolve(1L, 100L, RestrictedEntity.INVOICE))
                .thenThrow(new AccessPolicyUnavailableException("policy store down", null));

        assertThatThrownBy(() ->
                        service.search(InvoiceCriteria.empty(), 1L, 100L, PageRequest.of(0, 20)))
                .isInstanceOf(AccessPolicyUnavailableException.class);

        // Fail closed end-to-end: no unfiltered query is ever issued.
        verifyNoInteractions(invoiceRepository);
    }

    private static InvoiceEntity invoice() {
        return new InvoiceEntity(
                "inv-001",
                "INV-1001",
                100L,
                "OH",
                "OPEN",
                new BigDecimal("1200.00"),
                LocalDate.of(2026, 1, 15));
    }
}
