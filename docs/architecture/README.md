# Architecture Documentation

Architecture guides for the **learning-java** application — a Spring Boot 3 / Java 21 / React 19 layered web application with an Apache Camel integration layer. All documents in this folder are derived from live codebase analysis and provide implementation-ready standards with code evidence.

---

## For AI Agents: How to Use These Docs

> **These docs use a canonical ownership model.** Every standard lives in exactly one file. All other files contain only a one-line cross-reference (`> See [doc.md STD-XX-YYY]`). If you read a cross-reference without having the target file loaded, you are missing the full definition.

**Before answering any question about architecture, patterns, or conventions:**
1. Load **all files** in this folder into your context window.
2. When you encounter `> See [some-doc.md STD-XX-YYY]`, the normative rule, code example, and anti-patterns are **only** in that linked file.
3. Use the table below to identify which file owns any standard before searching.

### STD ID Ownership Table

| Range | File | Canonical topics |
|---|---|---|
| STD-ARC-* | [architecture.md](architecture.md) | Observability, Actuator health, security posture, blocking/async policy, performance |
| STD-API-* | [api-standards.md](api-standards.md) | URL patterns, error envelope, pagination, HTTP methods, auth, versioning, OpenAPI |
| STD-BE-* | [backend-patterns.md](backend-patterns.md) | Controller, service, repository, MapStruct, Bean Validation, SLF4J, `@PreAuthorize`, enums |
| STD-FE-* | [frontend-patterns.md](frontend-patterns.md) | Component library policy, TanStack Query hooks, API client layer, error handling, generated types |
| STD-DI-* | [dependency-injection.md](dependency-injection.md) | Bean scopes, constructor injection, `@Configuration` per feature, `@Transactional` boundaries |
| STD-INT-* | [integration-guide.md](integration-guide.md) | Apache Camel routes, `RestClient`, Resilience4j, external service config, caching |
| STD-LAY-* | [layer-design.md](layer-design.md) | Layer boundary rules, dependency rules, package organisation (prose + cross-refs only, no code blocks) |
| STD-DB-* | [database-patterns.md](database-patterns.md) | Standard audit columns, soft-delete, naming conventions, column data types |
| STD-SEC-* | [row-level-security-pattern.md](row-level-security-pattern.md) | Row-level filtering, no service→repository dependency, policy value objects, fail-closed, authz placement |
| STD-TST-* | [testing-guide.md](testing-guide.md) | JUnit 5 + Mockito, `@CamelSpringBootTest`, `renderWithProviders`, `data-testid`, coverage goals |

---

## Stack Summary

| Layer | Technology | Status |
|-------|-----------|--------|
| Backend Web API | Java 21 / Spring Boot 3.3.2 / Spring MVC | ✅ Implemented |
| Build | Maven (wrapper 3.9.16) | ✅ Implemented |
| Integration | Apache Camel 4.8.5 (`camel-spring-boot-starter`, `platform-http`, `camel-http`, `camel-jackson`) | ✅ Implemented |
| Database | H2 in-memory (dev) via Spring Data JPA / Hibernate 6 | ✅ Implemented (`product`, `invoice`, `user_restriction`) |
| Schema management | `spring.jpa.hibernate.ddl-auto=update` (dev only) | ⚠️ See STD-DB-004 |
| Mapping | MapStruct | ❌ Prescribed, not on classpath |
| Validation | Jakarta Bean Validation (`spring-boot-starter-validation`) | ✅ On classpath, unused |
| Boilerplate | Lombok | ✅ On classpath |
| Logging | SLF4J + Logback (Spring Boot default) | ✅ Implemented (defaults only) |
| Auth | Spring Security OAuth2 Resource Server / JWT | ❌ Not implemented (GAP-ARC-006) |
| API docs | springdoc-openapi | ❌ Not implemented (GAP-API-001) |
| Frontend | React 19 / Vite 8 / JavaScript (not TypeScript) | ✅ Implemented |
| UI Components | None adopted | ⚠️ Pending decision — see [TECHNICAL_DECISIONS_PENDING.md](TECHNICAL_DECISIONS_PENDING.md) |
| Server State | TanStack Query | ❌ Prescribed; app currently uses raw `fetch` (GAP-FE-001) |
| Lint | oxlint | ✅ Implemented |
| Unit Testing | JUnit 5 + Spring Boot Test + `camel-test-spring-junit5` (backend); Vitest + RTL (frontend) | ✅ backend / ❌ frontend |
| E2E Testing | Playwright | ❌ Not implemented |

> **Read the Status column.** This project is a learning/reference application. Roughly half the standards below are *prescriptive* (what to build when the feature arrives) rather than *descriptive* (what exists today). Every standard carries a **Confidence** rating, and unimplemented prescriptions carry a `GAP-` marker. Do not add a dependency or annotation just because a standard mentions it — add it when the story calls for it.

---

## Core Documents

### [layer-design.md](layer-design.md)
**What**: Layer responsibilities, boundaries, dependency rules, and object mapping flow.

**When to use**:
- Starting a new feature → understand where code belongs
- Reviewing layer violations → check what's forbidden at each layer
- Scaffolding a new endpoint → follow the Quick Reference checklist

**Key concepts**:
- Three-layer flow: `web` (Presentation) → `service` (Business) → `repository` (Data)
- Three-type hierarchy: Request/Response → Domain → Entity — always via MapStruct
- Feature packages under `web/`, `service/`, and `repository/`
- `BaseController` response helpers, `XxxService` interfaces, Spring Data `XxxRepository` interfaces

---

### [api-standards.md](api-standards.md)
**What**: RESTful API conventions, URL patterns, response format, pagination, status codes, auth.

**When to use**:
- Creating a new API endpoint
- Choosing the right HTTP status code
- Understanding the error response envelope format

**Key concepts**:
- All routes: `/api/v1/[resource]` (URL-versioned, declared per-method, not on the class)
- Custom `ApiResponse`/`ApiError` envelope — NOT RFC 7807 `ProblemDetail`
- Default pagination: `limit = 100`, max `500`
- Camel routes live under `/camel/*` and are exempt from the `/api/v1` rule (STD-API-007)

---

### [testing-guide.md](testing-guide.md)
**What**: Test organization, patterns, mock strategies, and naming conventions by layer.

**When to use**:
- Writing new tests (backend unit, slice, Camel route, frontend component, E2E)
- Setting up test infrastructure
- Debugging test failures

**Key concepts**:
- Backend: JUnit 5 `@Test` + Mockito + AssertJ, `methodName_should_expectedBehavior` naming
- Camel: `@CamelSpringBootTest` with `RANDOM_PORT`, `AdviceWith` + `MockEndpoint` for outbound calls
- Frontend: Vitest + RTL with a `renderWithProviders()` wrapper + hook mocking
- Frontend E2E: Playwright

---

### [backend-patterns.md](backend-patterns.md)
**What**: Complete Spring Boot 3 backend implementation patterns with concrete code examples.

**When to use**:
- Scaffolding a new feature (controller, service, repository)
- Understanding the package structure under `web/`, `service/`, and `repository/`
- Cross-referencing bean registration, MapStruct setup, Bean Validation

**Key concepts**:
- Single Maven module `com.example:app`; layer-first, feature-second packages
- `BaseController`; Spring Data JPA `Specification` composition; MapStruct `@Mapper`
- SLF4J structured logging; `@RestControllerAdvice` global handler; custom `ApiResponse`

---

### [frontend-patterns.md](frontend-patterns.md)
**What**: React 19 / Vite frontend implementation patterns with concrete code examples.

**When to use**:
- Adding a new page or component
- Setting up API calls and data fetching
- Writing frontend tests

**Key concepts**:
- TanStack Query hooks wrap API-client calls; components consume only hooks
- The app is plain JavaScript today — no TypeScript, no component library (both pending decisions)
- Backend base URL comes from `import.meta.env.VITE_API_BASE_URL`, never a hard-coded `localhost:8080`

---

### [architecture.md](architecture.md)
**What**: Cross-cutting standards — logging, observability, health, security, performance, blocking policy.

**When to use**:
- Implementing logging or error handling
- Configuring Actuator or tracing
- Understanding the (currently absent) auth flow

**Key concepts**:
- SLF4J + Logback + MDC correlation ID (prescribed; `CorrelationIdFilter` not yet written)
- Actuator, OpenTelemetry, and Spring Security are **NOT YET IMPLEMENTED** (GAP-ARC-003/006/008)
- Error format: `ApiResponse`/`ApiError`, not `ProblemDetail`
- Blocking-by-default: Spring MVC on a servlet container, not WebFlux (STD-ARC-010)

---

### [dependency-injection.md](dependency-injection.md)
**What**: Bean registration patterns, scopes, and configuration-class conventions.

**When to use**:
- Registering a new service or repository
- Deciding between component scan and an explicit `@Bean`
- Placing `@Transactional` boundaries

---

### [database-patterns.md](database-patterns.md)
**What**: JPA / Hibernate database standards — audit columns, soft-delete, naming conventions, and column type rules.

**When to use**:
- Creating or migrating a table
- Defining JPA `@Entity` classes
- Reviewing column naming, data types, or constraint naming

**Key concepts**:
- Every table MUST have six standard audit columns: `id`, `is_deleted`, `modified_date`, `modified_by`, `created_date`, `created_by`
- Soft-delete via `is_deleted = true`; enforced globally with Hibernate `@SQLRestriction`
- `snake_case` tables and columns (singular table names); `timestamp(6)` UTC; `bigint` PKs
- `ddl-auto=update` is dev-only — production schema requires Flyway (VAR-DB-001)

---

### [row-level-security-pattern.md](row-level-security-pattern.md)
**What**: How to restrict *which rows* a user may see without injecting a service into a repository.

**When to use**:
- A list endpoint must return a per-user or per-tenant subset of a table
- You are tempted to inject a policy/permission service into a repository
- Reviewing where an authorization check belongs

**Worked example**: the `invoice` feature — `GET /api/v1/invoices?userId=1&accountId=100`

**Key concepts**:
- Resolve policy in `service/`, pass it down as an immutable `EntityRestrictions` record — never inject a service into `repository/`
- The filter is a `Specification<T>`, composed with ordinary filters via `Specification.allOf(...)`
- Security specs return `cb.conjunction()`, never `null` — the `null`-is-skipped idiom is for optional filters only
- `Page<T>` from one `Specification` feeds both the rows query and the count query; no hand-written `countXxx`
- **Fail closed** — `catch (Exception e) { return emptyList(); }` on a policy path is a data leak

---

### [integration-guide.md](integration-guide.md)
**What**: Outbound HTTP patterns, Apache Camel route conventions, resilience, and external service configuration.

**When to use**:
- Adding a new external service call
- Writing or modifying a Camel route
- Understanding the retry / fallback behaviour already in the repo

---

### [text-truncation-pattern.md](text-truncation-pattern.md)
**What**: Shared UI pattern for truncating long text with a hover tooltip in grids, tables, and cards.

**When to use**:
- Building any tabular or card view that displays variable-length text

---

### [source-repos.md](source-repos.md)
**What**: Repository and module layout — where the backend and frontend live and how they are built together.

---

### [TECHNICAL_DECISIONS_PENDING.md](TECHNICAL_DECISIONS_PENDING.md)
**What**: Architectural decisions that require team input before the corresponding feature can be built.

---

## Development Workflows

### Adding a New Feature
1. Read [layer-design.md](layer-design.md) → Quick Reference
2. Read [backend-patterns.md](backend-patterns.md) → STD-BE-001 through STD-BE-011
3. Read [database-patterns.md](database-patterns.md) → if the feature involves a new or modified table
4. Read [row-level-security-pattern.md](row-level-security-pattern.md) → if any list endpoint returns a per-user or per-tenant subset
5. Read [frontend-patterns.md](frontend-patterns.md) for React patterns

### Creating a New API Endpoint
1. Follow [api-standards.md](api-standards.md) → Endpoint Checklist
2. Confirm bean wiring per [dependency-injection.md](dependency-injection.md)

### Creating or Modifying a Database Table
1. Follow [database-patterns.md](database-patterns.md) → STD-DB-001 (audit columns) and STD-DB-002 (naming)
2. Apply column type rules from STD-DB-003
3. Add the `@Entity` under `repository/<feature>/entity/`; add a Flyway migration under `backend/src/main/resources/db/migration/` once Flyway is adopted (STD-DB-004)

### Adding an Integration / Outbound Call
1. Follow [integration-guide.md](integration-guide.md) → STD-INT-001 (Camel) or STD-INT-002 (`RestClient`)
2. Never hard-code a base URL — read it from `application.properties` (STD-INT-004)

### Writing Tests
1. Follow [testing-guide.md](testing-guide.md) → layer-specific section
2. Use `renderWithProviders` for React, constructor-injected Mockito mocks for services, `AdviceWith` for Camel

---

## Pending Technical Decisions

The following are **architectural decisions that require team input**. They are tracked in full in [TECHNICAL_DECISIONS_PENDING.md](TECHNICAL_DECISIONS_PENDING.md).

| # | Decision | Impact | Who decides |
|---|----------|--------|-------------|
| 1 | Production datasource (stay on H2 vs. PostgreSQL vs. MySQL) | Schema, dialect, CI, deployment | Backend + DevOps |
| 2 | Schema management (`ddl-auto` vs. Flyway vs. Liquibase) | Migration safety, review process | Backend lead |
| 3 | Frontend language (stay on JavaScript vs. adopt TypeScript) | Type safety, generated API models, lint config | Frontend team |
| 4 | Frontend component library (none vs. MUI vs. headless + CSS) | Design system, bundle size, a11y baseline | Frontend + design |
| 5 | Authentication strategy (none vs. OAuth2 Resource Server vs. session) | Security, deployment, test harness | Security + backend lead |
| 6 | Integration boundary (which traffic goes through Camel vs. plain Spring MVC) | Testability, observability, team ramp-up | Backend architecture |

---

## How to Use This Document

**For Business Analysts & PMs**:
- Read the Stack Summary and the Status column
- Use "Pending Technical Decisions" to understand what the engineering team needs to decide
- Don't decide these yourself — bring them to the engineering team during planning

**For Engineers**:
- Use the implementation pattern docs (layer-design, api-standards, backend-patterns …) for day-to-day coding
- Check the **Confidence** rating and any `GAP-` marker before treating a standard as existing behaviour
- Contribute your expertise to finalize the pending decisions during planning

**For Architects**:
- Review the pending decisions to facilitate team consensus
- Identify dependencies between the Camel layer and the Spring MVC layer before engineering starts
