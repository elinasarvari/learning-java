package com.example.app.web.invoice;

import com.example.app.service.invoice.InvoiceService;
import com.example.app.service.invoice.domain.InvoiceCriteria;
import com.example.app.service.security.AccessPolicyUnavailableException;
import com.example.app.web.common.dto.SearchResults;
import com.example.app.web.invoice.dto.InvoiceResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for exercising row-level security by hand.
 *
 * <p>Try it. Same query, three callers, three different totals:
 *
 * <pre>
 * GET /api/v1/invoices?userId=1&amp;accountId=100   -> 2 of 6  (region OH only)
 * GET /api/v1/invoices?userId=2&amp;accountId=100   -> 2 of 6  (region OH|MI AND status PAID)
 * GET /api/v1/invoices?userId=3&amp;accountId=100   -> 6 of 6  (no policy rows: unrestricted)
 * </pre>
 *
 * <p>Check {@code totalRecords}, not just the row count: it comes from the count query,
 * and the point of STD-SEC-004 is that the same specification filters both.
 *
 * <p><b>userId as a request parameter is a demo shim.</b> Spring Security is not on the
 * classpath yet (GAP-ARC-006), so there is no authenticated principal to read. In a real
 * deployment this MUST come from the security context - a caller who can name their own
 * userId can name someone else's and read their rows. See STD-SEC-006.
 */
@RestController
@CrossOrigin(origins = "${cors.allowed-origins}")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/api/v1/invoices")
    public SearchResults<InvoiceResponse> searchInvoices(
            @RequestParam Long userId,
            @RequestParam Long accountId,
            @RequestParam(required = false) String term,
            @RequestParam(required = false) List<Long> customerIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        InvoiceCriteria criteria =
                new InvoiceCriteria(term, customerIds == null ? List.of() : customerIds);

        Page<InvoiceResponse> result = invoiceService
                .search(criteria, userId, accountId, PageRequest.of(page, size))
                .map(InvoiceResponse::from);

        return SearchResults.from(result);
    }

    /**
     * 503, and deliberately no detail in the body.
     *
     * <p>Belongs in a {@code @RestControllerAdvice} once one exists (STD-BE-009); kept
     * local so this feature is self-contained, matching {@code CatalogController}.
     */
    @ExceptionHandler(AccessPolicyUnavailableException.class)
    public ResponseEntity<String> handlePolicyUnavailable(AccessPolicyUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Access policy is temporarily unavailable");
    }
}
