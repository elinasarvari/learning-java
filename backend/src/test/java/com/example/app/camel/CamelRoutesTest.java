package com.example.app.camel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Routes that need no network. Camel starts normally here - contrast with
 * GithubRouteMockTest, which has to intercept a route before startup.
 */
@CamelSpringBootTest
// RANDOM_PORT, not the default MOCK: the rest() routes use platform-http, which
// needs a real servlet container. Under MOCK they fail to start, and that abort
// takes the direct: routes down with it.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CamelRoutesTest {

    @Autowired ProducerTemplate template;

    @Test
    void helloRouteGreetsByName() {
        String body = template.requestBodyAndHeader(
            "direct:helloName", null, "name", "Elina", String.class);

        assertEquals("Hello Elina (via Camel)", body);
    }

    @Test
    void flakyRouteSucceedsAfterRedeliveries() {
        // FlakyService succeeds on every 3rd call, so 3 retries always cover it
        // regardless of where the shared counter happens to start.
        String body = template.requestBody("direct:flaky", null, String.class);

        assertTrue(body.contains("\"status\":\"OK\""), "got: " + body);
    }

    @Test
    void boomRouteFallsBackWhenRetriesAreExhausted() {
        String body = template.requestBody("direct:boom", null, String.class);

        assertTrue(body.contains("UNAVAILABLE"), "got: " + body);
    }
}
