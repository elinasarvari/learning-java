package com.example.app.service.invoice;

import com.example.app.repository.invoice.InvoiceRepository;
import com.example.app.repository.invoice.entity.InvoiceEntity;
import com.example.app.repository.invoice.spec.InvoiceSpecifications;
import com.example.app.repository.security.spec.SecuritySpecifications;
import com.example.app.service.invoice.domain.Invoice;
import com.example.app.service.invoice.domain.InvoiceCriteria;
import com.example.app.service.security.SecurityPolicyService;
import com.example.app.service.security.domain.EntityRestrictions;
import com.example.app.service.security.domain.RestrictedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The composition point. This is where "what the user asked for" and "what the user may
 * see" are combined - one layer above the repository, so the repository never learns
 * that access control exists.
 *
 * <p>Compare with the anti-pattern in
 * {@code docs/architecture/row-level-security-pattern.md}, where the policy service is
 * injected into the repository instead and the arrows form a cycle.
 */
@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final SecurityPolicyService securityPolicyService;

    public InvoiceServiceImpl(
            InvoiceRepository invoiceRepository, SecurityPolicyService securityPolicyService) {
        this.invoiceRepository = invoiceRepository;
        this.securityPolicyService = securityPolicyService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Invoice> search(
            InvoiceCriteria criteria, Long userId, Long accountId, Pageable pageable) {

        // (1) Resolve policy ONCE, here. Not inside the repository, and not once per
        //     query - findAll(spec, pageable) issues two, and both must see the same
        //     policy (STD-SEC-004).
        EntityRestrictions restrictions =
                securityPolicyService.resolve(userId, accountId, RestrictedEntity.INVOICE);

        // (2) Compose. Optional user filters may be null and are skipped; the security
        //     specification is never null and is never skipped.
        Specification<InvoiceEntity> spec = Specification.allOf(
                InvoiceSpecifications.numberContains(criteria.term()),
                InvoiceSpecifications.forCustomers(criteria.customerIds()),
                SecuritySpecifications.restrictedBy(restrictions));

        log.debug(
                "Invoice search for user {} on account {} with {} restriction(s)",
                userId,
                accountId,
                restrictions.attributes().size());

        // (3) Hand the repository a specification and a pageable. Nothing else.
        return invoiceRepository.findAll(spec, pageable).map(InvoiceServiceImpl::toDomain);
    }

    private static Invoice toDomain(InvoiceEntity entity) {
        return new Invoice(
                entity.getId(),
                entity.getInvoiceNumber(),
                entity.getCustomerId(),
                entity.getRegion(),
                entity.getStatus(),
                entity.getAmount(),
                entity.getIssuedOn());
    }
}
