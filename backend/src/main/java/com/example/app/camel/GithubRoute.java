package com.example.app.camel;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.springframework.stereotype.Component;

/**
 * Step 2 - outbound (producer) endpoints, and the single most common Camel HTTP
 * gotcha.
 *
 *   curl localhost:8080/camel/gh/user/octocat    -> works
 *   curl localhost:8080/camel/gh/broken/octocat  -> 502, wrong URL was called
 *   curl -i localhost:8080/camel/gh/fail         -> 502 from a real upstream 404
 */
@Component
public class GithubRoute extends RouteBuilder {

    @Override
    public void configure() {

        // Scoped to the routes in THIS RouteBuilder. Retrying a 404 is pointless,
        // so this one just maps the failure to a clean response; see RetryRoute
        // for the redelivery side.
        onException(HttpOperationFailedException.class)
            .handled(true)
            .log("Upstream returned an error status: ${exception.message}")
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(502))
            .setBody(simple("{\"error\":\"upstream call failed\","
                + "\"upstreamStatus\":${exception.statusCode}}"));

        // Catch-all. Without this, anything that is NOT an HTTP status error -
        // an SSL handshake failure, a DNS failure, a malformed URL - escapes as a
        // bare 500 with an empty body. Camel always picks the MOST SPECIFIC
        // matching onException, so this does not shadow the one above.
        onException(Exception.class)
            .handled(true)
            .log("Call failed: ${exception.message}")
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
            .setBody(simple("{\"error\":\"${exception.message}\"}"));

        rest("/gh")
            .get("/user/{user}").to("direct:ghUser")
            .get("/broken/{user}").to("direct:ghBroken")
            .get("/fail").to("direct:ghFail");

        // ---- The correct version -------------------------------------------
        from("direct:ghUser")
            .routeId("gh-user")
            // GitHub rejects requests without a User-Agent with a 403.
            .setHeader("User-Agent", constant("camel-learning-demo"))
            .setHeader("Accept", constant("application/vnd.github+json"))
            .removeHeader(Exchange.HTTP_PATH)
            // .toD (not .to) because the URI contains an expression. .to()
            // resolves its URI ONCE at startup and caches the endpoint, so
            // ${header.user} would never be substituted.
            .toD("{{github.base-url}}/users/${header.user}"
                + "?bridgeEndpoint=true"
                + "&connectTimeout={{github.connect-timeout-ms}}")
            // Give the step a stable id so tests can swap it for a mock without
            // matching on the URI - which is still an unresolved {{...}}
            // placeholder at this point, so URI matching would not work.
            .id("call-github")
            .convertBodyTo(String.class)
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));

        // ---- The trap, reproduced deterministically -------------------------
        from("direct:ghBroken")
            .routeId("gh-broken")
            .setHeader("User-Agent", constant("camel-learning-demo"))
            // Note what is NOT here: bridgeEndpoint=true, and no removeHeader.
            // The inbound request left its own path in the exchange headers, and
            // the HTTP producer treats those as an override of the endpoint URI.
            // The call attempted is therefore
            //   /camel/gh/broken/octocat/users/octocat
            // Camel's own error message names the fix. Add bridgeEndpoint=true
            // and it ignores the inbound path/query headers instead.
            .toD("{{github.base-url}}/users/${header.user}")
            .convertBodyTo(String.class);

        // ---- A genuine upstream error --------------------------------------
        from("direct:ghFail")
            .routeId("gh-fail")
            .setHeader("User-Agent", constant("camel-learning-demo"))
            .removeHeader(Exchange.HTTP_PATH)
            // Set throwExceptionOnFailure=false instead if you would rather
            // receive the error body as normal data and branch on the status.
            .to("{{github.base-url}}/users/no-such-user-99999999?bridgeEndpoint=true")
            .convertBodyTo(String.class);
    }
}
