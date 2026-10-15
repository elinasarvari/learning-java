package com.example.app.repository.invoice;

import com.example.app.repository.invoice.entity.InvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Note what is <i>not</i> here.
 *
 * <p>There is no implementation class, no {@code EntityManager}, no
 * {@code SecurityPolicyService} field, and no {@code userId} parameter on any method.
 * Security filtering reaches this repository as a {@code Specification} composed by the
 * service layer (STD-SEC-001, STD-SEC-003), so the data layer never asks who is calling.
 *
 * <p>{@code JpaSpecificationExecutor} contributes
 * {@code findAll(Specification, Pageable)}, which runs the rows query and the count
 * query from the same specification - so the security predicate cannot be applied to one
 * and forgotten on the other (STD-SEC-004).
 */
public interface InvoiceRepository
        extends JpaRepository<InvoiceEntity, String>, JpaSpecificationExecutor<InvoiceEntity> {}
