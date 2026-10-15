package com.example.app.config;

import com.example.app.repository.invoice.InvoiceRepository;
import com.example.app.repository.invoice.entity.InvoiceEntity;
import com.example.app.repository.security.UserRestrictionRepository;
import com.example.app.repository.security.entity.UserRestrictionEntity;
import com.example.app.service.security.domain.RestrictedEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Seeds six invoices and three policy shapes, so the effect of the filter is visible
 * with a browser and no setup.
 *
 * <p>Java rather than {@code data.sql} for the same reason as {@link CatalogSeedData}:
 * with {@code ddl-auto=update} Hibernate creates the schema after Boot runs
 * {@code data.sql}.
 *
 * <pre>
 * invoices           OH: INV-1001 OPEN, INV-1002 PAID
 *                    MI: INV-1003 PAID, INV-1004 OPEN
 *                    IN: INV-1005 PAID, INV-1006 VOID
 *
 * user 1   region IN (OH)                      -> sees 2
 * user 2   region IN (OH, MI) AND status (PAID) -> sees 2   (different 2)
 * user 3   no policy rows                       -> sees 6
 * </pre>
 *
 * <p>Users 1 and 2 both see two rows but not the same two - which is the case a test
 * asserting only {@code size() == 2} would miss.
 */
@Configuration
@Profile("!test")
public class InvoiceSeedData {

    private static final Logger log = LoggerFactory.getLogger(InvoiceSeedData.class);
    private static final Long ACCOUNT = 100L;

    @Bean
    ApplicationRunner seedInvoices(
            InvoiceRepository invoiceRepository, UserRestrictionRepository restrictionRepository) {
        return args -> {
            if (invoiceRepository.count() > 0) {
                return;
            }
            LocalDate day = LocalDate.of(2026, 1, 15);
            invoiceRepository.saveAll(List.of(
                    new InvoiceEntity("inv-001", "INV-1001", ACCOUNT, "OH", "OPEN", new BigDecimal("1200.00"), day),
                    new InvoiceEntity("inv-002", "INV-1002", ACCOUNT, "OH", "PAID", new BigDecimal("845.50"), day),
                    new InvoiceEntity("inv-003", "INV-1003", ACCOUNT, "MI", "PAID", new BigDecimal("2310.00"), day),
                    new InvoiceEntity("inv-004", "INV-1004", ACCOUNT, "MI", "OPEN", new BigDecimal("980.25"), day),
                    new InvoiceEntity("inv-005", "INV-1005", ACCOUNT, "IN", "PAID", new BigDecimal("450.00"), day),
                    new InvoiceEntity("inv-006", "INV-1006", ACCOUNT, "IN", "VOID", new BigDecimal("175.75"), day)));

            restrictionRepository.saveAll(List.of(
                    // user 1 - single field, single value
                    new UserRestrictionEntity(1L, ACCOUNT, RestrictedEntity.INVOICE, "region", "OH"),
                    // user 2 - two fields, ANDed; two values within the region field, ORed
                    new UserRestrictionEntity(2L, ACCOUNT, RestrictedEntity.INVOICE, "region", "OH"),
                    new UserRestrictionEntity(2L, ACCOUNT, RestrictedEntity.INVOICE, "region", "MI"),
                    new UserRestrictionEntity(2L, ACCOUNT, RestrictedEntity.INVOICE, "status", "PAID")));
            // user 3 intentionally has no rows

            log.info(
                    "Seeded {} invoices and {} restriction rows",
                    invoiceRepository.count(),
                    restrictionRepository.count());
        };
    }
}
