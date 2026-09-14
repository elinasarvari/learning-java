package com.example.app.web.cache.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Stores a raw string value.
 *
 * @param ttlSeconds optional; falls back to app.cache.default-ttl (5 minutes)
 */
public record StoreRequest(
        @NotBlank(message = "value is required")
        String value,

        @Positive(message = "ttlSeconds must be positive")
        Long ttlSeconds) {
}
