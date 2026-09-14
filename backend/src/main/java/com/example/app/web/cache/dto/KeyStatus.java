package com.example.app.web.cache.dto;

/**
 * Whether a key is present, and for how long.
 *
 * <p>Separate from {@code MatchResponse} so neither field has to do double duty: a
 * comparison result and "the key exists" are different questions, and one record
 * answering both invites reading {@code matched} as {@code exists}.
 *
 * @param ttlSeconds remaining lifetime, null when absent or without an expiry
 */
public record KeyStatus(String key, boolean exists, Long ttlSeconds) {

    public static KeyStatus of(String key, boolean exists, Long ttlSeconds) {
        return new KeyStatus(key, exists, ttlSeconds);
    }
}
