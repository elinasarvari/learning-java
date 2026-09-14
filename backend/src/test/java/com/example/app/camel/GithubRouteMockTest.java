package com.example.app.camel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * How to test a route that calls an external system, without the network.
 *
 * @UseAdviceWith stops Camel from auto-starting so the route can be rewritten
 * first; @DirtiesContext keeps the rewritten route out of other test classes.
 */
@CamelSpringBootTest
// RANDOM_PORT, not the default MOCK: the rest() routes use platform-http, which
// needs a real servlet container. Under MOCK they fail to start, and that abort
// takes the direct: routes down with it.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@UseAdviceWith
@DirtiesContext
class GithubRouteMockTest {

    @Autowired CamelContext context;
    @Autowired ProducerTemplate template;

    @Test
    void ghUserRouteCallsUpstreamAndReturnsItsBody() throws Exception {
        // Match by id, not by URI: the route's URI is still the unresolved
        // "{{github.base-url}}/..." placeholder at advice time, so weaveByToUri
        // would find nothing.
        AdviceWith.adviceWith(context, "gh-user", route ->
            route.weaveById("call-github")
                 .replace()
                 .to("mock:github"));

        MockEndpoint mock = context.getEndpoint("mock:github", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.whenAnyExchangeReceived(exchange ->
            exchange.getMessage().setBody("{\"login\":\"octocat\"}"));

        context.start();

        String body = template.requestBodyAndHeader(
            "direct:ghUser", null, "user", "octocat", String.class);

        mock.assertIsSatisfied();
        assertTrue(body.contains("octocat"), "expected mocked payload, got: " + body);
    }
}
