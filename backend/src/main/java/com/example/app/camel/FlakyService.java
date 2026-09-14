package com.example.app.camel;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * A deterministic stand-in for an unreliable upstream, so the redelivery
 * behaviour in RetryRoute can be observed without any network.
 */
@Component("flakyService")
public class FlakyService {

    private final AtomicInteger attempts = new AtomicInteger();

    /** Fails on attempts 1 and 2, succeeds on the 3rd - then repeats. */
    public String maybeFail() {
        int n = attempts.incrementAndGet();
        if (n % 3 != 0) {
            throw new IllegalStateException("transient failure on attempt " + n);
        }
        return "{\"status\":\"OK\",\"succeededOnAttempt\":" + n + "}";
    }

    public String alwaysFail() {
        throw new IllegalStateException("permanent failure");
    }
}
