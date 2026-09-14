# Architecture, Performance & Reliability Standards

This section defines cross-cutting constraints that apply to all features. These standards extend the core layer and API patterns with observability, performance, security, and reliability requirements.

---

## Cross-Cutting Principles

- Architecture decisions that impact multiple features (shared services, storage formats, concurrency strategies) MUST be captured in the plan or a dedicated design note linked from it.
- Error handling MUST be explicit: failures MUST either be surfaced to users in a clear, actionable way or handled safely with logging.
- Logging and diagnostics MUST be sufficient to understand failures in production without re-running user data by hand.
- Backwards-incompatible changes to APIs, file formats, or user-visible behavior MUST include a migration or compatibility plan.

---

## STD-ARC-003 — Distributed Tracing (GAP — Not Yet Implemented)

> ⚠️ **GAP-ARC-003**: No `micrometer-tracing`, `opentelemetry`, or `spring-boot-starter-actuator` dependency exists in `backend/pom.xml`. Telemetry today is limited to Logback console output at the levels configured in `application.properties`.

**Normative Rule** (aspirational — see GAP-ARC-003): When implemented, tracing SHOULD be configured via `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`, producing spans for:
- Inbound HTTP requests (Spring MVC auto-instrumentation)
- Outbound HTTP calls (`RestClient` / `camel-http`)
- Database operations (Hibernate / JDBC instrumentation)
- Apache Camel exchanges (`camel-opentelemetry-starter` — one span per route)
- Custom spans for expensive operations (file parsing, report generation)

**Current alternative**: Camel logs one line per route step at the level set by `logging.level.org.apache.camel` (currently `INFO`), plus explicit `.log(...)` steps inside routes.

**Do NOT** add `@Observed`, `Tracer`, or an OTel agent flag unless the story explicitly calls for it.

**Traceability**:
- `backend/pom.xml` (no tracing dependency present)
- `backend/src/main/resources/application.properties` (`logging.level.org.apache.camel=INFO`)

**Confidence**: Low (prescribed but not implemented)

---

## STD-ARC-006 — Authentication (GAP — Not Yet Implemented)

> ⚠️ **GAP-ARC-006**: `spring-boot-starter-security` is **not** on the classpath. Every endpoint — `/api/hello`, every `/camel/*` route, and the H2 console at `/h2-console` — is currently unauthenticated. This is acceptable for a local learning application and unacceptable for anything deployed.

**Normative Rule** (aspirational): When authentication is introduced, the backend MUST validate bearer tokens as an OAuth2 **Resource Server**, not by hand-rolling token parsing:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=${AUTH_ISSUER_URI}
```

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(CsrfConfigurer::disable)          // stateless bearer-token API
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .build();
    }
}
```

**Anti-Patterns**:
- Hard-coding an issuer URI, client ID, or signing key in a `.java` or committed `.properties` file — read them from the environment
- Parsing the `Authorization` header manually inside a controller or a Camel route
- Leaving `/h2-console` reachable in any profile other than `dev`
- Disabling CSRF on a session-based (cookie) app — the rule above applies only because the API is stateless

**Choice of strategy is still open** — see [TECHNICAL_DECISIONS_PENDING.md](TECHNICAL_DECISIONS_PENDING.md) Decision 5.

**Cross-references**:
- Method-level authorization → [backend-patterns.md STD-BE-010](backend-patterns.md)
- Input validation → [backend-patterns.md STD-BE-006](backend-patterns.md)
- SQL injection prevention → [backend-patterns.md STD-BE-004](backend-patterns.md) (Spring Data JPA / `Specification` only, no string-concatenated SQL)

**Traceability**:
- `backend/pom.xml` (no security starter)
- `backend/src/main/resources/application.properties` (`spring.h2.console.enabled=true`)

**Confidence**: Low (prescribed but not implemented)

---

## STD-ARC-007 — CORS Configuration

**Normative Rule**: The allowed browser origin MUST come from the `cors.allowed-origins` property. Per-controller `@CrossOrigin` annotations MUST NOT be used for new code — register one global `CorsConfigurationSource` (or `WebMvcConfigurer#addCorsMappings`) so the policy is defined in exactly one place and applies to Camel's `platform-http` routes too.

**Current configuration**:

```properties
# backend/src/main/resources/application.properties
cors.allowed-origins=http://localhost:5173
```

**Accepted Variant (VAR-ARC-001)**: `HelloController` carries a literal `@CrossOrigin(origins = "http://localhost:5173")` that duplicates — and ignores — the property above. It predates this standard. Migrate it when `HelloController` is next touched; do not copy the pattern.

```java
// backend/src/main/java/com/example/app/HelloController.java — do NOT copy
@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class HelloController { … }
```

**Prescribed replacement**:

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(allowedOrigins);
        registry.addMapping("/camel/**").allowedOrigins(allowedOrigins);
    }
}
```

**Anti-Patterns**:
- `@CrossOrigin(origins = "*")` on anything that will ever carry credentials
- A hard-coded origin string anywhere outside `application.properties`

**Traceability**:
- `backend/src/main/resources/application.properties`
- `backend/src/main/java/com/example/app/HelloController.java`

**Confidence**: High (property exists and is read by nothing yet — the gap is the point of this standard)

---

## STD-ARC-008 — Health Checks (GAP — Not Yet Implemented)

> ⚠️ **GAP-ARC-008**: `spring-boot-starter-actuator` is not on the classpath. There is no health endpoint.

**Normative Rule** (aspirational): The API MUST expose a health endpoint at `/actuator/health`. Datasource connectivity is contributed automatically by `DataSourceHealthIndicator` once Actuator and a `DataSource` are both present. Apache Camel contributes its own `CamelHealthCheckIndicator` when `camel-spring-boot-starter` is on the classpath — meaning route-level health comes free.

**Prescribed configuration**:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized
camel.health.enabled=true
```

**Anti-Patterns**:
- Exposing `management.endpoints.web.exposure.include=*` — that publishes `/actuator/env` and `/actuator/heapdump`
- Writing a hand-rolled `GET /health` controller when Actuator does it better

**Traceability**:
- `backend/pom.xml` (no actuator dependency)

**Confidence**: Low (prescriptive, no implementation evidence)

---

## STD-ARC-009 — Performance for File / Document Processing

**Normative Rule**: File processing operations MUST measure and log elapsed time. Expensive operations (report generation, large file parsing) SHOULD use custom spans when tracing is activated (see STD-ARC-003). Caching strategies for expensive computations MUST be documented in the feature plan.

**Measurement pattern** (works today, no extra dependency):

```java
long startedAt = System.nanoTime();
var result = parser.parse(input);
log.info("Parsed {} rows from {} in {} ms",
    result.size(), fileName, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
```

Prefer Micrometer `Timer` over manual timing once Actuator is on the classpath (STD-ARC-008).

**Current state**: No file processing exists in the codebase. This rule is forward-looking.

**Confidence**: Low (prescriptive, no implementation evidence)

---

## STD-ARC-010 — Blocking Servlet Model (No WebFlux)

**Normative Rule**: This application runs the **blocking** Spring MVC servlet stack on embedded Tomcat. Service and repository methods MUST be plain synchronous methods with no `Async` suffix. `spring-boot-starter-webflux`, `Mono`, `Flux`, and `WebClient` MUST NOT be introduced — mixing the reactive and servlet stacks in one context causes subtle, hard-to-diagnose thread-context bugs and would break Camel's `platform-http` component, which binds to the servlet container Spring Boot already started.

**Rationale**: `camel.rest.component=platform-http` in `application.properties` deliberately reuses the servlet container so Camel routes and `HelloController` share port 8080. Switching to WebFlux would move the server to Netty and silently change that contract.

**When a genuinely asynchronous call is required**: use `@Async` + `CompletableFuture<T>`, and only then suffix the method with `Async`:

```java
@Async
public CompletableFuture<ReportDomain> generateReportAsync(long id) { … }
```

For fire-and-forget or fan-out messaging, prefer a Camel route (`seda:` / `direct:`) over ad-hoc threads — see [integration-guide.md STD-INT-001](integration-guide.md).

**Anti-Patterns**:
- Adding `Async` to a method that returns `T` rather than `CompletableFuture<T>`
- `new Thread(...)` or a bare `ExecutorService` inside a service — use `@Async` with a configured `TaskExecutor`
- Blocking on `.block()` / `.join()` inside a request thread

**Traceability**:
- `backend/pom.xml` (`spring-boot-starter-web`, not webflux)
- `backend/src/main/resources/application.properties` (`camel.rest.component=platform-http`)

**Confidence**: High

---

## AI Implementation Hints

- **Logging**: See [backend-patterns.md STD-BE-007](backend-patterns.md) — use SLF4J `{}` placeholders, never string concatenation. MDC-based correlation is prescribed but the filter is not written yet.
- **Pagination**: See [api-standards.md STD-API-005](api-standards.md) — default page size is `100`, maximum `500`.
- **Error format**: See [api-standards.md STD-API-004](api-standards.md) — use the `ApiResponse` / `ApiError` envelope, **not** Spring's `ProblemDetail`.
- **Tracing**: NOT implemented — do not add `micrometer-tracing` or OTel wiring unless the story explicitly calls for it (STD-ARC-003).
- **Auth**: NOT implemented — do not add `@PreAuthorize` to a controller before the security starter exists; it silently does nothing without `@EnableMethodSecurity` (STD-ARC-006).
- **Actuator**: NOT implemented — do not reference `/actuator/*` in a runbook or health probe until STD-ARC-008 is done.
- **Threading**: Blocking servlet stack only. No `Mono`, no `Flux`, no `WebClient` (STD-ARC-010).
