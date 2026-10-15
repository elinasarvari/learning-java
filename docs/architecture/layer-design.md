# Layer Architecture Guide

**Repos**: `backend/` + `frontend/` | **Status**: Partly implementation-validated, partly prescriptive

This guide documents the layered architecture for the learning-java application. The Camel integration layer is live code; the three-layer `web → service → repository` stack is prescribed and only partially present today (see the callout at the end of each section).

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│  PRESENTATION LAYER                                          │
│  Backend:  com.example.app.web.<feature>                     │
│            (@RestController, request/response records)       │
│  Frontend: src/pages/<feature>/ (React components)           │
└──────────────────────────────────────────────────────────────┘
                        ↓  interfaces only
┌──────────────────────────────────────────────────────────────┐
│  BUSINESS LAYER                                              │
│  Backend:  com.example.app.service.<feature>                 │
│            (XxxService interface + domain records)           │
│  Frontend: src/api/hooks/ + src/api/services/                │
└──────────────────────────────────────────────────────────────┘
                        ↓  interfaces only
┌──────────────────────────────────────────────────────────────┐
│  DATA ACCESS LAYER                                           │
│  Backend:  com.example.app.repository.<feature>              │
│            (Spring Data JPA repositories + @Entity)          │
│  Frontend: shared fetch client (via TanStack Query)          │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  INTEGRATION LAYER  (cuts across, does not nest)             │
│  com.example.app.camel — RouteBuilder beans                  │
│  Inbound  /camel/*  via platform-http                        │
│  Outbound via camel-http                                     │
└──────────────────────────────────────────────────────────────┘
```

**Object model flow (backend)**:

```
XxxRequest / XxxResponse   (web.<feature>.dto)
        ↕ MapStruct
XxxDomain                  (service.<feature>.domain)
        ↕ MapStruct
XxxEntity                  (repository.<feature>.entity)   ← JPA entity
```

---

## Layer Responsibilities

### STD-LAY-001 — Presentation Layer — Backend (`web/`)

**Normative Rule**: The presentation layer MUST only handle HTTP request binding, response serialization, and Request/Response ↔ Domain mapping via MapStruct. All business logic MUST be delegated to an `XxxService` interface. `@Entity` types MUST NOT be referenced from any class under `web/`.

**What it does**:
- Handles HTTP requests and produces HTTP responses
- Maps request/response records ↔ domain records via a MapStruct `@Mapper`
- Delegates all business logic to `XxxService`
- Declares OpenAPI metadata (`@Operation`, `@ApiResponse`) once springdoc is adopted
- Applies method-level authorization (`@PreAuthorize`) once Spring Security is adopted

**What it does NOT do**:
- ❌ Contain business rules or domain logic
- ❌ Access repositories directly
- ❌ Reference `@Entity` types from the data layer
- ❌ Perform calculations or data transformations
- ❌ Open a transaction (`@Transactional` belongs on the service — see STD-DI-004)

> **Implementation pattern**: See [backend-patterns.md STD-BE-002](backend-patterns.md).
>
> **Current state**: only `HelloController` exists, and it returns a bare `String` rather than an `ApiResponse`. It is a scaffold, not an example to copy.

---

### STD-LAY-002 — Presentation Layer — Frontend (`pages/` + `components/`)

**Normative Rule**: Frontend page and component files MUST only render UI and consume TanStack Query hooks for data. They MUST NOT call `fetch` or the API client directly, and MUST NOT use `useState` + `useEffect` to manage server state.

**What it does**:
- Renders UI
- Calls query hooks for data
- Handles loading, error, and success states
- Routes to pages via the router

**What it does NOT do**:
- ❌ Call `fetch` or the shared API client directly
- ❌ Call service functions directly (only via hooks)
- ❌ Manage server state with `useState` + `useEffect`

> **Implementation pattern**: See [frontend-patterns.md STD-FE-005](frontend-patterns.md) (page organisation) and [STD-FE-006](frontend-patterns.md) (error handling).
>
> ⚠️ **GAP-FE-001**: `frontend/src/App.jsx` currently does exactly what this rule forbids — `useState` + `useEffect` + a raw `fetch('http://localhost:8080/api/hello')`. It is the Vite starter template, not a reference implementation.

---

### STD-LAY-003 — Business Layer — Backend (`service/`)

**Normative Rule**: Business services MUST accept and return only domain types. Services MUST be stateless, MUST NOT reference request/response records or JPA entities in their public signatures, and MUST NOT touch `HttpServletRequest`, `HttpSession`, or any HTTP concept.

**What it does**:
- Implements business rules and domain validation
- Coordinates between multiple repositories when needed
- Returns domain records (never `@Entity`, never `XxxResponse`)
- Owns transaction boundaries via `@Transactional`
- Stateless — no mutable instance fields

**What it does NOT do**:
- ❌ Accept or return request/response records or JPA entities
- ❌ Know about HTTP (no `HttpServletRequest`, no header inspection)
- ❌ Open database connections directly (always via a repository interface)
- ❌ Return a lazily-initialised entity graph to the caller — map before the transaction closes

> **Implementation pattern**: See [backend-patterns.md STD-BE-003](backend-patterns.md).
>
> **Accepted Variant (VAR-LAY-001)**: `FlakyService` is a `@Component` invoked directly from a Camel route by bean name. It is a deliberate test double for the retry demo, not a business service, and is exempt from this standard.

---

### STD-LAY-004 — Business Layer — Frontend (`api/hooks/` + `api/services/`)

**Normative Rule**: The frontend business layer MUST consist of a service file (raw HTTP via the shared client) and a hooks file (TanStack Query wrappers). Components MUST consume the hook layer only — direct service calls from components are PROHIBITED.

**What it does**:
- `src/api/services/<feature>.service.js` — raw HTTP calls via the shared client
- `src/api/hooks/use<Feature>Queries.js` — TanStack Query wrappers exposing `isLoading`, `isError`, `data`
- Defines `queryKey` arrays for cache management

> **Implementation pattern**: See [frontend-patterns.md STD-FE-003](frontend-patterns.md) (client layer) and [STD-FE-004](frontend-patterns.md) (query hooks).

---

### STD-LAY-005 — Data Access Layer — Backend (`repository/`)

**Normative Rule**: Repositories MUST interact with the database exclusively through Spring Data JPA. They MUST return `@Entity` types (or projections), MUST use the `Specification` API for composable query filters, and MUST NOT contain business logic or return domain / response types.

**What it does**:
- Extends `JpaRepository<XxxEntity, Long>` and `JpaSpecificationExecutor<XxxEntity>`
- Uses `Specification<XxxEntity>` for composable, reusable query filters
- Declares derived query methods (`findByEmailIgnoreCase`) or `@Query` JPQL for anything a `Specification` can't express
- Returns `@Entity` types, `Optional<XxxEntity>`, `Page<XxxEntity>`, or interface projections

**What it does NOT do**:
- ❌ Contain business rules or domain logic
- ❌ Return domain or response types
- ❌ Call other repositories (coordinate in the service layer)
- ❌ Declare `@Transactional` for read paths (the service owns the boundary)
- ❌ Build SQL by string concatenation — `Specification` or `@Query` with bound parameters only

> **Implementation pattern**: See [backend-patterns.md STD-BE-004](backend-patterns.md).
>
> **Per-user / per-tenant row filtering**: See [row-level-security-pattern.md STD-SEC-001](row-level-security-pattern.md) — policy is resolved in `service/` and passed down as a value object; a repository MUST NOT inject the service that resolves it.
>
> **Current state**: `spring-boot-starter-data-jpa` and H2 are configured, but no `@Entity` or repository interface exists yet. This layer is entirely prescriptive today.

---

### STD-LAY-011 — Integration Layer (Apache Camel)

**Normative Rule**: Camel `RouteBuilder` beans MUST live in `com.example.app.camel`. A `rest(...)` declaration MUST only declare the external contract and immediately hand off to a `direct:` endpoint — the implementation MUST live in a separate `from("direct:…")` route with an explicit `.routeId(...)`. Routes MUST NOT call repositories directly; anything touching persistence MUST go through an `XxxService` bean via `.bean(...)`.

**Rationale**: Splitting contract from implementation lets the same logic be re-exposed over Kafka, JMS, or a scheduled timer without touching the business route. It is also what makes the routes testable — see [testing-guide.md STD-TST-013](testing-guide.md).

**Pattern** (live code):

```java
// backend/src/main/java/com/example/app/camel/HelloRoute.java
rest("/hello")
    .get().to("direct:hello")
    .get("/{name}").to("direct:helloName");

from("direct:hello")
    .routeId("hello")
    .setBody(constant("Hello Elina (via Camel)"));
```

**Every route MUST have a `.routeId(...)`.** Without one, Camel generates `route1`, `route2`, … which change whenever a route is added — breaking `AdviceWith` tests and making logs unreadable.

**Anti-Patterns**:
- ❌ `restConfiguration()` inside a `RouteBuilder` — RouteBuilder load order is not guaranteed, so a builder that loads first silently gets defaults instead. Configure REST via `camel.rest.*` properties (already done in `application.properties`).
- ❌ `.to(...)` with an expression in the URI — it resolves once at startup and caches the endpoint. Use `.toD(...)`.
- ❌ Omitting `bridgeEndpoint=true` on an outbound HTTP producer that sits behind an inbound HTTP consumer — the inbound path/query headers override the endpoint URI. `GithubRoute.direct:ghBroken` reproduces this deliberately.
- ❌ Business logic expressed as a wall of `simple(...)` expressions — put it in a `@Component` and call `.bean(...)`.

**Traceability**:
- `backend/src/main/java/com/example/app/camel/HelloRoute.java`
- `backend/src/main/java/com/example/app/camel/GithubRoute.java`
- `backend/src/main/java/com/example/app/camel/RetryRoute.java`

**Confidence**: High

---

## STD-LAY-006 — Dependency Rules

**Normative Rule**: Dependencies MUST only flow top-down through layer interfaces. `web/` MUST depend only on `XxxService`. `service/` MUST depend only on `XxxRepository`. Direct cross-layer type references (controller→repository, service→response record) are PROHIBITED.

**ALLOWED (top-down only)**:

```
Presentation  →  Business Interface  →  Data Interface
web/            XxxService              XxxRepository
```

**FORBIDDEN**:
- ❌ `repository/` referencing `service/` — the commonest real-world instance of this is a policy/permission service injected into a repository to build a security predicate. See [row-level-security-pattern.md STD-SEC-001](row-level-security-pattern.md) for the correct shape.
- ❌ `service/` referencing `web/` types (request/response records)
- ❌ `web/` referencing `repository/` directly
- ❌ `web/` referencing `@Entity` types
- ❌ `camel/` referencing `repository/` directly

**Dependency Injection** (constructor injection only — see [dependency-injection.md STD-DI-002](dependency-injection.md)):

```java
// Controller injects the service interface
public assetController(assetService assetService, assetWebMapper mapper) { … }

// Service injects the repository interface
public assetServiceImpl(assetRepository assetRepository, assetMapper mapper) { … }
```

Field injection (`@Autowired` on a field) is PROHIBITED — it hides required dependencies, defeats final fields, and cannot be constructed in a plain unit test.

**Enforcement suggestion**: an ArchUnit test in `backend/src/test/java/com/example/app/ArchitectureTest.java` can assert these rules mechanically. Not yet written.

---

## Cross-Cutting Concerns

### STD-LAY-007 — Error Handling

**Normative Rule**: Error handling is layer-specific. Repositories MUST let `DataIntegrityViolationException` propagate (or translate it into a domain exception at the service boundary). Services MUST throw a typed `ApiException` carrying the intended HTTP status, or return `Optional.empty()` for "not found". Controllers MUST convert an empty `Optional` into `handleErrorResponse(HttpStatus.NOT_FOUND, …)`. All unhandled exceptions MUST be caught and formatted by a single `@RestControllerAdvice`.

**Strategy by layer**:

| Layer | Responsibility |
|-------|---------------|
| Repository | Let Spring's `DataAccessException` hierarchy surface; never swallow |
| Service | Translate `DataIntegrityViolationException` → `ApiException(CONFLICT, …)`; business rule violations throw `ApiException` |
| Controller | Empty `Optional` → `handleErrorResponse(HttpStatus.NOT_FOUND, …)` |
| `@RestControllerAdvice` | Catches everything unhandled → structured `ApiResponse` with a 500 `ApiError` |
| Camel route | `onException(...).handled(true)` sets `Exchange.HTTP_RESPONSE_CODE` and a JSON body — the advice never sees it |

**Error response envelope**: See [api-standards.md STD-API-004](api-standards.md) for the normative format (custom `ApiResponse`/`ApiError`, NOT RFC 7807 `ProblemDetail`).

> **Note on the Camel layer**: `GithubRoute` and `RetryRoute` already implement this pattern with `onException(...)`, ordering handlers most-specific-first, and always including a catch-all `onException(Exception.class)`. Without the catch-all, a DNS or TLS failure escapes as a bare 500 with an empty body.

### STD-LAY-008 — Logging

**Normative Rule**: All layers MUST use SLF4J with `{}` placeholder templates. String concatenation and `String.format` in log messages MUST NOT be used. See [backend-patterns.md STD-BE-007](backend-patterns.md) for the full normative rule, log levels, configuration, and anti-patterns.

```java
// Service
log.error("Failed to create application '{}' for group {}",
    application.name(), application.groupCode(), ex);
```

### STD-LAY-009 — Validation

**Normative Rule**: Validation MUST be two-stage: Jakarta Bean Validation at the presentation layer validates format and structure (auto-returning 400 via the global handler), and the service layer enforces business rules (throwing `ApiException` with an appropriate status).

**Two-stage validation** (see [backend-patterns.md STD-BE-006](backend-patterns.md) for full details):
- **Format validation** (Presentation): constraint annotations on the request record + `@Valid` on the controller parameter. `MethodArgumentNotValidException` is mapped to a 400 `ApiResponse` by the `@RestControllerAdvice`.
- **Business validation** (Business): uniqueness, cross-entity constraints, and state-machine checks live in `service/`, throwing `ApiException` with the right status.

A validator MUST NOT query the database. Uniqueness is a business rule, not a format rule.

---

## STD-LAY-010 — Package Organization

**Normative Rule**: Source files MUST be placed in their prescribed layer package: `web/` for presentation, `service/` for business, `repository/` for data access, `camel/` for integration routes, and `config/` for `@Configuration` classes. Feature packages MUST be applied within each layer.

> **Canonical package tree**: See [backend-patterns.md — Project Structure](backend-patterns.md) for the full annotated layout including test sources.

**Accepted Variant (VAR-LAY-002)**: `Application.java` and `HelloController.java` currently sit in the root package `com.example.app`. `Application` belongs there (it anchors component scanning). `HelloController` does not — relocate it to `com.example.app.web.hello` when it is next touched.

---

## Quick Reference

**When adding a new feature `Foo`:**
1. Create `service/foo/FooService.java` (interface)
2. Create `service/foo/FooServiceImpl.java` (implementation, annotated `@Service`)
3. Create `service/foo/domain/FooDomain.java` (a `record`)
4. Create `repository/foo/FooRepository.java` extending `JpaRepository` + `JpaSpecificationExecutor`
5. Create `repository/foo/entity/FooEntity.java` with the six audit columns (STD-DB-001)
6. Create `repository/foo/spec/FooSpecifications.java` for composable filters
7. Create `web/foo/FooController.java` extending `BaseController`
8. Create `web/foo/dto/FooResponse.java`, `CreateFooRequest.java`, `UpdateFooRequest.java` (records)
9. Create `web/foo/FooWebMapper.java` and `service/foo/FooMapper.java` (MapStruct `@Mapper(componentModel = "spring")`)
10. Add a Flyway migration under `backend/src/main/resources/db/migration/` (once STD-DB-004 lands)

**Anti-Patterns**:
- ❌ Business rule in controller: `if (repo.existsByName(name)) return conflict();`
- ❌ `EntityManager` or `DataSource` injected into a service
- ❌ `HttpServletRequest` in a service signature
- ❌ Business logic in a repository: `if (item.getPrice() > 1000) item.setDiscount(0.1);`
- ❌ Request/response records in service signatures
- ❌ `@Entity` types in controller or service signatures
- ❌ Returning a JPA entity straight out of a controller (leaks the schema and triggers lazy-loading serialization failures)
