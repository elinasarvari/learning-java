package com.example.app.web.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Paginated response envelope (STD-API-003).
 *
 * <p>Built from a {@link Page} rather than serialising the {@code Page} directly: Spring
 * Boot 3.3 warns that {@code PageImpl}'s JSON shape is not a stable API contract.
 */
public record SearchResults<T>(int limit, int offset, long totalRecords, List<T> results) {

    public static <T> SearchResults<T> from(Page<T> page) {
        return new SearchResults<>(
                page.getSize(),
                (int) page.getPageable().getOffset(),
                page.getTotalElements(),
                page.getContent());
    }
}
