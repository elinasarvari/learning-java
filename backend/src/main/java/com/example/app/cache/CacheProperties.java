package com.example.app.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cache tuning, bound from {@code app.cache.*} in application.properties.
 *
 * <p>A record gets constructor binding for free in Spring Boot 3, and the compact
 * constructor is the only place a Java record can express default values - callers
 * (including the binder) always invoke the canonical constructor, so defaults applied
 * here hold even when a property is absent entirely.
 */
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(String namespace, Duration defaultTtl, Duration lockTtl) {

    public CacheProperties {
        // Every key is prefixed with this. Without a namespace, evictPrefix() and any
        // SCAN would reach into other services' keys on a shared Redis instance.
        namespace = (namespace == null || namespace.isBlank()) ? "learning-java" : namespace;

        // The 5-minute window the catalog example caches for.
        defaultTtl = (defaultTtl == null) ? Duration.ofMinutes(5) : defaultTtl;

        // Upper bound on how long one loader can hold the stampede lock. Must exceed the
        // slowest expected load, or a second caller starts loading while the first is
        // still running - which is exactly what the lock exists to prevent.
        lockTtl = (lockTtl == null) ? Duration.ofSeconds(10) : lockTtl;
    }
}
