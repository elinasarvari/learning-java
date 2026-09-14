package com.example.app.web.catalog;

import com.example.app.cache.RedisCache;
import com.example.app.service.catalog.CatalogService;
import com.example.app.web.catalog.dto.ProductResponse;
import com.example.app.web.catalog.dto.UpdatePriceRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for exercising the cache by hand.
 *
 * <p>Try it: call {@code GET /api/catalog/products/p-001} twice. The first call takes
 * ~400ms and logs {@code CACHE MISS}; the second returns in single-digit milliseconds and
 * logs nothing. Then {@code PUT} a new price and call it again - the miss is back,
 * because the write evicted the entry.
 */
@RestController
@RequestMapping("/api/catalog")
@CrossOrigin(origins = "${cors.allowed-origins}")
public class CatalogController {

    private final CatalogService catalogService;
    private final RedisCache cache;

    public CatalogController(CatalogService catalogService, RedisCache cache) {
        this.catalogService = catalogService;
        this.cache = cache;
    }

    @GetMapping("/products/{id}")
    public ProductResponse getProduct(@PathVariable String id) {
        return catalogService.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No product with id " + id));
    }

    @GetMapping("/products")
    public List<ProductResponse> getByCategory(@RequestParam String category) {
        return catalogService.findByCategory(category).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @PutMapping("/products/{id}/price")
    public ProductResponse updatePrice(
            @PathVariable String id,
            @Valid @RequestBody UpdatePriceRequest request) {
        return ProductResponse.from(catalogService.updatePrice(id, request.price()));
    }

    /** Hit/miss counters, to confirm the cache is doing what you think it is. */
    @GetMapping("/cache/stats")
    public RedisCache.CacheStats stats() {
        return cache.stats();
    }

    @DeleteMapping("/cache/stats")
    public ResponseEntity<Void> resetStats() {
        cache.resetStats();
        return ResponseEntity.noContent().build();
    }

    /**
     * Local handler so a missing product returns 404 rather than 500.
     *
     * <p>Belongs in a {@code @RestControllerAdvice} once one exists (STD-BE-009); kept
     * here for now so this feature is self-contained.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
