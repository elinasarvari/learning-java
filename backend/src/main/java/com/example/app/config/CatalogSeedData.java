package com.example.app.config;

import com.example.app.repository.catalog.ProductRepository;
import com.example.app.repository.catalog.entity.ProductEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Seeds the H2 in-memory database so the catalog endpoints have something to return.
 *
 * <p>Done in Java rather than {@code data.sql} because with {@code ddl-auto=update}
 * Hibernate creates the schema <i>after</i> Boot runs {@code data.sql}, so the inserts
 * fail on a missing table unless {@code spring.jpa.defer-datasource-initialization} is
 * also set. An {@code ApplicationRunner} sidesteps the ordering question entirely.
 */
@Configuration
@Profile("!test")
public class CatalogSeedData {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeedData.class);

    @Bean
    ApplicationRunner seedCatalog(ProductRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            Instant now = Instant.now();
            repository.saveAll(List.of(
                    new ProductEntity("p-001", "Ergonomic Keyboard", "peripherals", new BigDecimal("129.00"), now),
                    new ProductEntity("p-002", "27\" 4K Monitor", "peripherals", new BigDecimal("449.50"), now),
                    new ProductEntity("p-003", "USB-C Dock", "peripherals", new BigDecimal("219.99"), now),
                    new ProductEntity("p-004", "Mechanical Mouse", "peripherals", new BigDecimal("79.00"), now),
                    new ProductEntity("p-005", "Standing Desk", "furniture", new BigDecimal("899.00"), now),
                    new ProductEntity("p-006", "Task Chair", "furniture", new BigDecimal("540.00"), now)));
            log.info("Seeded {} catalog products", repository.count());
        };
    }
}
