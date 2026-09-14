package com.example.app.web.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdatePriceRequest(
        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", message = "price must not be negative")
        BigDecimal price) {
}
