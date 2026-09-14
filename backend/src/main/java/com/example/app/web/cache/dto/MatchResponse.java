package com.example.app.web.cache.dto;

/**
 * Result of a key/value comparison.
 *
 * <p>{@code matched} is deliberately the only signal: a missing key, an expired key and a
 * wrong value all return {@code false}. Distinguishing them would tell a caller which
 * keys exist, which is exactly what an attacker probing for valid codes wants to know.
 *
 * @param ttlSeconds remaining lifetime, null when the key is absent or has no expiry
 */
public record MatchResponse(String key, boolean matched, Long ttlSeconds) {

    public static MatchResponse of(String key, boolean matched, Long ttlSeconds) {
        return new MatchResponse(key, matched, ttlSeconds);
    }
}
