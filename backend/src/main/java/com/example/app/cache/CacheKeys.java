package com.example.app.cache;

/**
 * Central definition of every cache key shape used by the application.
 *
 * <p>Keys are built here rather than inlined at call sites so that a read and the
 * eviction that invalidates it cannot drift apart - the classic cache bug is a writer
 * that evicts {@code product:42} while the reader stored {@code products:42}, leaving
 * stale data until the TTL expires.
 *
 * <p>The namespace prefix is NOT applied here; {@link RedisCache} adds it, so these
 * stay readable and namespace changes stay in one place.
 */
public final class CacheKeys {

    private CacheKeys() {
        // static-only holder
    }

    /** A single product by id. */
    public static String product(String id) {
        return "product:" + id;
    }

    /** The product list for one category. */
    public static String productsByCategory(String category) {
        return "product:category:" + category;
    }

    /**
     * Prefix covering every per-category list.
     *
     * <p>Used when a product changes and we cannot know which category lists it appears
     * in - the category itself may have been what changed.
     */
    public static String allCategoryLists() {
        return "product:category:";
    }
}
