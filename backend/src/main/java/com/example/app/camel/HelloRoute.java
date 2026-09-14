package com.example.app.camel;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Step 1 - an inbound (consumer) endpoint.
 *
 * Try both of these while the app runs; they are served by the same JVM on the
 * same port, one by Spring MVC and one by Camel:
 *   curl localhost:8080/api/hello        -> HelloController
 *   curl localhost:8080/camel/hello      -> this route
 */
@Component
public class HelloRoute extends RouteBuilder {

    @Override
    public void configure() {
        // The externally-visible contract. Note this only declares the shape -
        // it delegates the work to an internal endpoint.
        rest("/hello")
            .get()
                .to("direct:hello")
            .get("/{name}")
                .to("direct:helloName");

        // "direct:" is NOT external - it is an in-JVM synchronous handoff.
        // Splitting contract from implementation lets you re-expose the same
        // logic over Kafka or JMS later without touching these routes.
        from("direct:hello")
            .routeId("hello")
            .setBody(constant("Hello Elina (via Camel)"));

        from("direct:helloName")
            .routeId("hello-name")
            // Path parameters arrive as HEADERS, not in the body. This trips
            // up nearly everyone once.
            .log("Greeting ${header.name}")
            .setBody(simple("Hello ${header.name} (via Camel)"));
    }
}
