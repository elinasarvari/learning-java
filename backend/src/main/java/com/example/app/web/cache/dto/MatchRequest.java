package com.example.app.web.cache.dto;

import jakarta.validation.constraints.NotBlank;

/** The candidate value to compare against what is stored under a key. */
public record MatchRequest(
        @NotBlank(message = "value is required")
        String value) {
}
