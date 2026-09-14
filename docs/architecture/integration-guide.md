# Integration Guide

**Repo**: `backend/` | **Stack**: Apache Camel 4.8.5 on Spring Boot 3.3.2 | **Status**: Implementation-Validated

This is the most complete part of the codebase. Every standard below except STD-INT-002 and STD-INT-005 is backed by working code in `com.example.app.camel`.

---

## STD-INT-001 — Apache Camel Is the Default Integration Boundary

**Normative Rule**: Outbound calls to external systems, and inbound endpoints that are protocol adapters rather than application APIs, MUST be implemented as Apache Camel routes in `com.example.app.camel`. Each `RouteBuilder` MUST be a `@Component`, and every route MUST carry an explicit `.routeId(...)`.

**When to Apply**: calling a third-party HTTP API; consuming a queue, file drop, or scheduled trigger; protocol translation; anything needing declarative retry, throttling, or content-based routing.

**When Not to Apply**: a plain CRUD API over this application's own database. That belongs in a `@RestController` → `@Service` → `JpaRepository` stack (see [layer-design.md](layer-design.md)). Do not route internal CRUD through Camel — you gain nothing and lose Spring MVC's validation, content negotiation, and OpenAPI integration.

> The boundary between "goes through Camel" and "plain Spring MVC" is a live design question — see [TECHNICAL_DECISIONS_PENDING.md](TECHNICAL_DECISIONS_PENDING.md) Decision 6.

**Contract / implementation split** (mandatory):

```java
// backend/src/main/java/com/example/app/camel/HelloRoute.java
// The externally-visible contract — declares shape only, delegates the work.
rest("/hello")
    .get()
        .to("direct:hello")
    .get("/{name}")
        .to("direct:helloName");

// "direct:" is NOT external — it is an in-JVM synchronous handoff.
from("direct:hello")
    .routeId("hello")
    .setBody(constant("Hello Elina (via Camel)"));

from("direct:helloName")
    .routeId("hello-name")
    // Path parameters arrive as HEADERS, not in the body.
    .log("Greeting ${header.name}")
    .setBody(simple("Hello ${header.name} (via Camel)"));
```

Splitting the contract from the implementation lets the same logic be re-exposed over Kafka, JMS, or a timer without touching either route — and it is what makes the routes independently testable (`template.requestBody("direct:hello", …)`).

**Route ids are mandatory.** Without `.routeId(...)`, Camel generates `route1`, `route2`, … which renumber whenever a route is added. That breaks `AdviceWith` tests, makes logs unreadable, and makes route-level health checks meaningless.

**Path parameters arrive as headers**, not in the body. This trips up nearly everyone once. `${header.name}`, not `${body}`.

**Traceability**: `backend/src/main/java/com/example/app/camel/HelloRoute.java`

**Confidence**: High

---

## STD-INT-002 — `RestClient` for Non-Camel Outbound Calls

**Normative Rule**: When an outbound HTTP call does not warrant a Camel route, it MUST use a `RestClient` bean configured in a `@Configuration` class with an explicit base URL and timeouts. `new RestTemplate()`, `RestTemplate` in new code, and raw `HttpURLConnection` are PROHIBITED. `WebClient` is PROHIBITED — it drags in the reactive stack (see [architecture.md STD-ARC-010](architecture.md)).

```java
// config/GithubConfig.java
@Configuration
public class GithubConfig {

    @Bean
    RestClient githubRestClient(GithubProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(factory)
            .defaultHeader("User-Agent", "learning-java")
            .build();
    }
}
```

**Timeouts are not optional.** A client with no read timeout will, on a hung upstream, hold a request thread until the container kills it. Under load that exhausts the Tomcat thread pool and takes down endpoints that have nothing to do with the integration.

**Anti-Patterns**:
- ❌ `new RestClient()` / `new RestTemplate()` inside a service method — no connection reuse, no shared config
- ❌ Any client without both a connect and a read timeout
- ❌ `WebClient` (reactive stack)
- ❌ A base URL literal in Java rather than in `application.properties`

**Confidence**: Low (prescriptive — no `RestClient` bean exists yet; outbound calls currently go through Camel)

---

## STD-INT-003 — Error Handling in Routes

**Normative Rule**: Every `RouteBuilder` that performs an outbound call MUST declare `onException` handlers, ordered from most specific to a mandatory `onException(Exception.class)` catch-all. A handler MUST set `Exchange.CONTENT_TYPE`, `Exchange.HTTP_RESPONSE_CODE`, and a JSON body consistent with the `ApiResponse` envelope ([api-standards.md STD-API-004](api-standards.md)). `handled(true)` MUST be set when the route is the outermost boundary.

**Live implementation**:

```java
// backend/src/main/java/com/example/app/camel/GithubRoute.java

// Scoped to the routes in THIS RouteBuilder. Retrying a 404 is pointless,
// so this one just maps the failure to a clean response.
onException(HttpOperationFailedException.class)
    .handled(true)
    .log("Upstream returned an error status: ${exception.message}")
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(502))
    .setBody(simple("{\"error\":\"upstream call failed\","
        + "\"upstreamStatus\":${exception.statusCode}}"));

// Catch-all. Without this, anything that is NOT an HTTP status error —
// an SSL handshake failure, a DNS failure, a malformed URL — escapes as a
// bare 500 with an empty body. Camel always picks the MOST SPECIFIC
// matching onException, so this does not shadow the one above.
onException(Exception.class)
    .handled(true)
    .log("Call failed: ${exception.message}")
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
    .setBody(simple("{\"error\":\"${exception.message}\"}"));
```

Three things to internalise:

1. **`onException` declared inside a `RouteBuilder` is scoped to that builder's routes only.** For a policy that spans every route, declare it in a dedicated `@Component` extending `RouteBuilder` with no `from(...)`, or use `errorHandler(...)` on the `CamelContext`.
2. **The catch-all is mandatory.** `HttpOperationFailedException` only covers a response that arrived with an error status. DNS failure, TLS failure, connection refused, and malformed URI all throw something else.
3. **Camel picks the most specific match**, so ordering in source is documentation, not semantics — but keep specific-first anyway for readers.

> **Exception messages in the response body**: the catch-all above echoes `${exception.message}` to the client. That is acceptable in a learning app and unacceptable in production — the message may contain a hostname, a file path, or a stack frame. Replace it with a generic string and log the detail before this reaches a real deployment.

**Camel routes bypass the Spring MVC `@RestControllerAdvice` entirely.** A handled exception never reaches it; an unhandled one surfaces as a `platform-http` 500 with no envelope. Keep the JSON shape consistent by hand.

**Anti-Patterns**:
- ❌ A route with an outbound call and no `onException`
- ❌ Only handling `HttpOperationFailedException` with no catch-all
- ❌ Omitting `handled(true)` on the outermost boundary — the caller gets a 500 and a stack trace instead of the body you built
- ❌ An error body shape that differs from route to route

**Traceability**: `backend/src/main/java/com/example/app/camel/GithubRoute.java`, `RetryRoute.java`

**Confidence**: High

---

## STD-INT-004 — Declarative Retry with Exponential Backoff

**Normative Rule**: Calls to a dependency that can fail transiently MUST declare redelivery via `onException(...)` with `maximumRedeliveries`, `redeliveryDelay`, `backOffMultiplier`, and `retryAttemptedLogLevel`. Retry counts and delays MUST NOT be hard-coded inline once more than one route needs them — externalise them to `application.properties` and reference with `{{...}}`. Exhausted retries MUST produce a defined fallback response, not a 500.

**Live implementation**:

```java
// backend/src/main/java/com/example/app/camel/RetryRoute.java
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
```

**Retry only what is actually transient.** `IllegalStateException` here stands in for a transient upstream fault. A 404, a 400, or a validation failure must never be retried — `GithubRoute` deliberately does *not* retry `HttpOperationFailedException` for exactly this reason. Retrying a deterministic failure just multiplies the latency before the same error is returned.

**Redelivery replays from the point of failure**, so a `.bean(...)` step is re-invoked on each attempt. That is what makes `FlakyService.maybeFail()` succeed on the third try.

**Externalised form** (prescribed, once a second route needs retry):

```properties
integration.retry.max-redeliveries=3
integration.retry.delay-ms=500
integration.retry.backoff-multiplier=2
```

```java
onException(IllegalStateException.class)
    .maximumRedeliveries(Integer.parseInt("{{integration.retry.max-redeliveries}}"))
```

For anything richer — circuit breaking, bulkheads, rate limiters — add `camel-resilience4j` and use `.circuitBreaker()` rather than hand-rolling it.

**Anti-Patterns**:
- ❌ Retrying a 4xx or a validation failure
- ❌ A fixed delay with no `backOffMultiplier` — synchronised retries from many callers hammer a recovering upstream
- ❌ Unbounded retries
- ❌ Omitting `retryAttemptedLogLevel` — silent retries hide a degrading dependency until it fails completely
- ❌ Exhausted retries producing a 500 rather than a defined 503 fallback

**Traceability**: `backend/src/main/java/com/example/app/camel/RetryRoute.java`, `FlakyService.java`

**Confidence**: High

---

## STD-INT-005 — External Service Configuration

**Normative Rule**: External base URLs, timeouts, and credentials MUST be read from configuration. In a Camel route, reference them with Camel's `{{property.name}}` placeholder syntax. Hard-coded hosts, ports, and timeouts in a route or a service are PROHIBITED. Credentials MUST come from environment variables, never from a committed `.properties` file.

**Current configuration**:

```properties
# backend/src/main/resources/application.properties
# Outbound targets. Camel resolves these with {{...}} (double braces),
# which is Camel's placeholder syntax, not Spring's ${...}.
github.base-url=https://api.github.com
github.connect-timeout-ms=5000
```

```java
.toD("{{github.base-url}}/users/${header.user}"
    + "?bridgeEndpoint=true"
    + "&connectTimeout={{github.connect-timeout-ms}}")
```

**Two different placeholder syntaxes coexist in this codebase.** Camel's `{{...}}` is resolved by Camel's `PropertiesComponent`; Spring's `${...}` is resolved by the `Environment`. Camel's component is backed by the Spring `Environment`, so both read the same `application.properties` — but writing `${github.base-url}` inside a route URI does not work, and writing `{{...}}` in `@Value` does not work either.

`${header.user}` inside a Camel URI is a **Simple expression**, not a Spring property — a third meaning of `${...}` in the same string. Read route URIs carefully.

**Anti-Patterns**:
- ❌ `https://api.github.com` written into a route
- ❌ An API key or token in `application.properties`
- ❌ An outbound endpoint with no `connectTimeout`
- ❌ `${github.base-url}` (Spring syntax) inside a Camel URI

**Traceability**: `backend/src/main/resources/application.properties`, `backend/src/main/java/com/example/app/camel/GithubRoute.java`

**Confidence**: High

---

## STD-INT-006 — Outbound HTTP Producer Rules

**Normative Rule**: An outbound HTTP producer that sits behind an inbound HTTP consumer MUST set `bridgeEndpoint=true`. A producer URI containing an expression MUST use `.toD(...)`, not `.to(...)`. Any step a test needs to intercept MUST carry a stable `.id(...)`.

These three rules exist because `GithubRoute` demonstrates all three failure modes deliberately.

### Rule 1 — `.toD` for dynamic URIs

```java
// ✅ correct — .toD re-evaluates the URI per exchange
.toD("{{github.base-url}}/users/${header.user}?bridgeEndpoint=true")
```

`.to()` resolves its URI **once at startup** and caches the resulting endpoint. `${header.user}` would never be substituted — every request would call the literal path.

### Rule 2 — `bridgeEndpoint=true`

```java
// ❌ the trap, reproduced deterministically in direct:ghBroken
.toD("{{github.base-url}}/users/${header.user}")
```

The inbound request left its own path in the exchange headers, and the HTTP producer treats those as an **override** of the endpoint URI. The call actually attempted is:

```
/camel/gh/broken/octocat/users/octocat
```

`bridgeEndpoint=true` tells the producer to ignore the inbound `Exchange.HTTP_PATH` / `HTTP_QUERY` headers. The correct route also does `.removeHeader(Exchange.HTTP_PATH)` as a belt-and-braces measure. Camel's own error message names the fix — read it.

### Rule 3 — Stable step ids for testability

```java
.toD("{{github.base-url}}/users/${header.user}?bridgeEndpoint=true")
.id("call-github")     // ← tests swap this step for a mock
```

A test cannot match this step by URI, because at advice time the URI is still the unresolved `{{github.base-url}}` placeholder — so `weaveByToUri` finds nothing. Matching by id is the only thing that works:

```java
AdviceWith.adviceWith(context, "gh-user", route ->
    route.weaveById("call-github").replace().to("mock:github"));
```

See [testing-guide.md STD-TST-014](testing-guide.md).

### Upstream-specific headers

```java
// GitHub rejects requests without a User-Agent with a 403.
.setHeader("User-Agent", constant("camel-learning-demo"))
.setHeader("Accept", constant("application/vnd.github+json"))
```

Set required headers explicitly. A missing `User-Agent` producing a 403 is indistinguishable from an auth failure until you read the upstream's docs.

### Error-body-as-data

By default an error status throws `HttpOperationFailedException`. To receive the error body as a normal message and branch on the status instead, set `throwExceptionOnFailure=false` on the producer URI.

**Anti-Patterns**:
- ❌ `.to(...)` with `${...}` in the URI
- ❌ Omitting `bridgeEndpoint=true` on a producer behind an HTTP consumer
- ❌ A producer step with no `.id(...)` that a test will need to mock
- ❌ Relying on `weaveByToUri` for a URI containing a `{{placeholder}}`

**Traceability**: `backend/src/main/java/com/example/app/camel/GithubRoute.java` (all three routes), `backend/src/test/java/com/example/app/camel/GithubRouteMockTest.java`

**Confidence**: High

---

## STD-INT-007 — Caching (Not Yet Implemented)

> ⚠️ **GAP-INT-007**: no caching abstraction is configured. `spring-boot-starter-cache` is not on the classpath.

**Normative Rule** (aspirational): repeated lookups against a slow or rate-limited external service MUST be cached with an explicit TTL. Use Spring's `@Cacheable` with a Caffeine backing store; configure the TTL and maximum size per cache, never globally.

```properties
spring.cache.cache-names=githubUsers
spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=5m
```

```java
@Cacheable("githubUsers")
public GithubUserDomain fetchUser(String login) { … }
```

**Pick one expiry policy and understand it.** `expireAfterWrite` bounds staleness; `expireAfterAccess` bounds idle memory. Specifying both means the shorter one always wins and the other is effectively dead configuration — a subtle way to think you have a 5-minute sliding window when you actually have a 1-minute hard cap.

**Anti-Patterns**:
- ❌ A `HashMap` field on a service used as a cache — unbounded, never evicted, and a data race (STD-DI-003)
- ❌ `@Cacheable` with no configured TTL — Spring's default `ConcurrentMapCacheManager` never evicts
- ❌ Caching a response that varies per authenticated user under a key that does not include the user

**Confidence**: Low (prescriptive)

---

## Verifying the Integration Layer by Hand

With `./mvnw spring-boot:run` in `backend/`:

```bash
curl localhost:8080/api/hello                  # Spring MVC — "Hello Elina"
curl localhost:8080/camel/hello                # Camel      — "Hello Elina (via Camel)"
curl localhost:8080/camel/hello/Ada            # header-bound path param

curl localhost:8080/camel/gh/user/octocat      # correct outbound call → GitHub JSON
curl -i localhost:8080/camel/gh/broken/octocat # 502 — bridgeEndpoint trap
curl -i localhost:8080/camel/gh/fail           # 502 — real upstream 404

curl localhost:8080/camel/flaky                # succeeds on attempt 3; watch the WARN lines
curl -i localhost:8080/camel/boom              # 4 attempts, then the 503 fallback
```

Both stacks are served by the same JVM on the same port — Spring MVC handles `/api/*`, Camel's `platform-http` handles `/camel/*`.

---

## AI Implementation Hints

- **Camel for integration, Spring MVC for this app's own CRUD.** Do not route internal CRUD through Camel.
- **Every route needs `.routeId(...)`.** Every mockable step needs `.id(...)`.
- **`.toD(...)` whenever the URI contains an expression.** `.to()` caches the endpoint at startup.
- **`bridgeEndpoint=true`** on any outbound producer behind an inbound HTTP consumer — otherwise the inbound path is appended to the outbound URI.
- **Always add `onException(Exception.class)`** as a catch-all, or DNS/TLS failures escape as an empty 500.
- **Retry only transient faults.** Never retry a 4xx.
- **`{{property}}` inside a route, `${property}` in Spring, `${header.x}` for Simple expressions.** Three syntaxes, one properties file.
- **Path parameters arrive as headers**, not in the body.
- **Timeouts on every outbound call**, connect and read.
- **Don't add `WebClient`** — it pulls in the reactive stack and breaks the `platform-http` assumption (STD-ARC-010).
