package com.example.app.camel;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Step 3 - declarative retries with exponential backoff, plus a fallback when
 * they are exhausted.
 *
 *   curl localhost:8080/camel/flaky   -> succeeds on attempt 3; watch the WARN
 *                                        redelivery lines in the console
 *   curl -i localhost:8080/camel/boom -> 4 attempts, then the 503 fallback
 */
@Component
public class RetryRoute extends RouteBuilder {

    @Override
    public void configure() {

        onException(IllegalStateException.class)
            .maximumRedeliveries(3)          // 1 initial attempt + 3 retries
            .redeliveryDelay(500)
            .backOffMultiplier(2)            // 500ms, 1s, 2s
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            // handled(true) stops the exception propagating and turns whatever
            // this block produces into the response. Drop it and the caller gets
            // a 500 with a stack trace instead.
            .handled(true)
            .log("Giving up after redeliveries: ${exception.message}")
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503))
            .setBody(constant("{\"status\":\"UNAVAILABLE\",\"retriesExhausted\":true}"));

        rest("/flaky").get().to("direct:flaky");
        rest("/boom").get().to("direct:boom");

        from("direct:flaky")
            .routeId("flaky")
            // Redelivery replays from the point of failure, so the bean is
            // re-invoked on each attempt.
            .bean("flakyService", "maybeFail")
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));

        from("direct:boom")
            .routeId("boom")
            .bean("flakyService", "alwaysFail");
    }
}
