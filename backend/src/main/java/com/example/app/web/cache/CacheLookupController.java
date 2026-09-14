package com.example.app.web.cache;

import com.example.app.cache.RedisCache;
import com.example.app.web.cache.dto.KeyStatus;
import com.example.app.web.cache.dto.MatchRequest;
import com.example.app.web.cache.dto.MatchResponse;
import com.example.app.web.cache.dto.StoreRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Looks up a key in Redis and compares its value against a supplied parameter.
 *
 * <p>The comparison endpoints return only a boolean. Reading a value back out is off by
 * default ({@code app.cache.lookup.expose-values}) because an endpoint that echoes
 * arbitrary cache values by key is a data-exposure hole: cached keys routinely hold
 * tokens, codes and personal data, and the caller supplies the key.
 *
 * <p>Prefer {@code /verify} over {@code /match} whenever the value is single-use.
 */
@RestController
@RequestMapping("/api/cache")
@CrossOrigin(origins = "${cors.allowed-origins}")
public class CacheLookupController {

    private final RedisCache cache;
    private final boolean exposeValues;

    public CacheLookupController(
            RedisCache cache,
            @Value("${app.cache.lookup.expose-values:false}") boolean exposeValues) {
        this.cache = cache;
        this.exposeValues = exposeValues;
    }

    /**
     * Compares the stored value with the supplied one, leaving the key in place.
     *
     * <pre>
     * POST /api/cache/session:abc/match   {"value": "expected-token"}
     * -> {"key":"session:abc","matched":true,"ttlSeconds":287}
     * </pre>
     */
    @PostMapping("/{key}/match")
    public MatchResponse match(@PathVariable String key, @Valid @RequestBody MatchRequest request) {
        boolean matched = cache.matches(key, request.value());
        return MatchResponse.of(key, matched, ttlSecondsOf(key));
    }

    /**
     * Compares, and deletes the key on success - a single-use check.
     *
     * <p>Read and delete are one atomic GETDEL, so two concurrent requests carrying the
     * same correct code cannot both succeed. Use this for one-time codes, idempotency
     * keys and nonces; {@code /match} leaves the value replayable.
     */
    @PostMapping("/{key}/verify")
    public MatchResponse verify(@PathVariable String key, @Valid @RequestBody MatchRequest request) {
        // TTL is read first: after a successful consume the key no longer exists.
        Long ttl = ttlSecondsOf(key);
        boolean matched = cache.consumeIfMatches(key, request.value());
        return MatchResponse.of(key, matched, matched ? null : ttl);
    }

    /**
     * Query-parameter form of {@code /match}, for a quick check from a browser or curl.
     *
     * <pre>GET /api/cache/session:abc/match?value=expected-token</pre>
     *
     * <p>Convenient, but the value lands in access logs and browser history - use the
     * POST form for anything sensitive.
     */
    @GetMapping("/{key}/match")
    public MatchResponse matchByQuery(@PathVariable String key, @RequestParam String value) {
        return MatchResponse.of(key, cache.matches(key, value), ttlSecondsOf(key));
    }

    /** Whether the key exists, without revealing its value. */
    @GetMapping("/{key}/exists")
    public KeyStatus exists(@PathVariable String key) {
        return KeyStatus.of(key, cache.getString(key).isPresent(), ttlSecondsOf(key));
    }

    /**
     * Stores a raw string value, so the comparison endpoints have something to check.
     *
     * <p>Returns 503 rather than 201 when the write did not land. Reads are allowed to
     * degrade silently - a miss is a valid answer - but a write is a promise, and
     * reporting success for a value that was never stored sends the caller off with a
     * code that can never verify.
     */
    @PutMapping("/{key}")
    public ResponseEntity<KeyStatus> store(
            @PathVariable String key,
            @Valid @RequestBody StoreRequest request) {

        Duration ttl = request.ttlSeconds() == null
                ? null
                : Duration.ofSeconds(request.ttlSeconds());

        boolean stored = (ttl == null)
                ? cache.putString(key, request.value())
                : cache.putString(key, request.value(), ttl);

        if (!stored) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(KeyStatus.of(key, false, null));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(KeyStatus.of(key, true, ttlSecondsOf(key)));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> evict(@PathVariable String key) {
        cache.evict(key);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the raw stored value. Disabled unless
     * {@code app.cache.lookup.expose-values=true}.
     *
     * <p>Keep it off outside local debugging: the caller chooses the key, and cached keys
     * hold tokens and personal data as a matter of course.
     */
    @GetMapping("/{key}")
    public ResponseEntity<String> peek(@PathVariable String key) {
        if (!exposeValues) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Value read-back is disabled. Set app.cache.lookup.expose-values=true "
                            + "to enable, or use /match to compare without reading.");
        }
        return cache.getString(key)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Long ttlSecondsOf(String key) {
        return cache.timeToLive(key).map(Duration::toSeconds).orElse(null);
    }
}
