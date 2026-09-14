# Pending Technical Decisions for learning-java

**Purpose**: Track architectural decisions that require team input before the corresponding feature can be built.

**Status**: Living document — update as decisions are made

---

## Quick Reference

| # | Decision | Blocks | Owner | Timeline |
|---|----------|--------|-------|----------|
| 1 | Production datasource | [database-patterns.md](database-patterns.md) STD-DB-003/004/005 | Backend + DevOps | Before the first persistent entity |
| 2 | Schema management | [database-patterns.md](database-patterns.md) STD-DB-004 | Backend lead | With Decision 1 |
| 3 | Frontend language (JS vs TS) | [frontend-patterns.md](frontend-patterns.md) STD-FE-008 | Frontend team | Before the first real page |
| 4 | Frontend component library | [frontend-patterns.md](frontend-patterns.md) STD-FE-001, [text-truncation-pattern.md](text-truncation-pattern.md) | Frontend + design | Before the first real page |
| 5 | Authentication strategy | [architecture.md](architecture.md) STD-ARC-006, STD-DB-006 | Security + backend lead | Before any deployment |
| 6 | Camel vs. Spring MVC boundary | [integration-guide.md](integration-guide.md) STD-INT-001 | Backend architecture | Before the second integration |
| 7 | CI and quality gates | [testing-guide.md](testing-guide.md) STD-TST-012 | Whole team | Before more than one contributor |

---

## Decision 1: Production Datasource

**Source**: [database-patterns.md — Technical Context](database-patterns.md)

**Question**: The app runs on in-memory H2, rebuilt on every restart. What does it run on when data must survive a restart?

**Impact**:
- 🗄️ **Schema portability**: SQL that works on H2 may not work on the target engine
- 🔍 **Feature availability**: partial unique indexes (needed for soft delete + uniqueness, STD-DB-005) exist on PostgreSQL, not H2
- 🧪 **Test fidelity**: `@DataJpaTest` against H2 will pass for queries that fail in production
- 📦 **Deployment**: an external database needs provisioning, connection pooling, and secret management

**Options**:

| Option | Pros | Cons | Risk |
|--------|------|------|------|
| **Stay on H2 (file mode)** | Zero new infrastructure; `jdbc:h2:file:./data/devdb` persists across restarts | Not a production database; no partial indexes; limited concurrency; a dead end | Low now, high later |
| **PostgreSQL** | Full SQL feature set incl. partial indexes and `jsonb`; excellent Hibernate support; free | Requires a running instance (Docker locally); dialect differences from H2 mean tests should move to Testcontainers | Low |
| **MySQL / MariaDB** | Widely deployed; simple operationally | No partial indexes (blocks the soft-delete uniqueness pattern); weaker transactional DDL | Medium |

**Recommendation to discuss**: PostgreSQL, with Testcontainers for persistence tests. It removes the H2-vs-production divergence that Decisions 1 and 2 otherwise create together.

**Who decides**: Backend lead + whoever will operate the database
**When**: before the first entity whose data must outlive a JVM restart

---

## Decision 2: Schema Management

**Source**: [database-patterns.md STD-DB-004](database-patterns.md)

**Question**: `spring.jpa.hibernate.ddl-auto=update` currently derives the schema from the entities at startup. What owns the schema once data is persistent?

**Impact**:
- 🔒 **Data safety**: `update` never drops, narrows, renames, or reorders — the live schema silently diverges from the entities
- 👀 **Reviewability**: with `update`, a schema change is invisible in a diff; with a migration file, it is a reviewable artifact
- ⏪ **Rollback**: `update` has no concept of reverting

**Options**:

| Option | Pros | Cons | Best for |
|--------|------|------|----------|
| **Flyway** | Plain SQL migrations; trivial mental model; checksum protection against editing an applied file; Spring Boot auto-configures it | SQL is database-specific unless written carefully | Most projects — the default |
| **Liquibase** | Database-agnostic changelogs (XML/YAML); can generate rollback statements | Heavier; an abstraction layer over SQL that must itself be learned | Multi-database products |
| **Keep `ddl-auto=update`** | Nothing to do | Unsafe against a persistent database; no review trail; no rollback | In-memory dev only |

**Recommendation to discuss**: Flyway with `ddl-auto=validate`. The pairing is what makes it safe — Flyway owns the schema, and Hibernate refuses to start if the entities have drifted from it, turning a silent mismatch into a startup failure.

**Coupled to Decision 1.** Choosing PostgreSQL means migration SQL should be written for PostgreSQL and tested against it, not against H2.

**Who decides**: Backend lead
**When**: at the same time as Decision 1

---

## Decision 3: Frontend Language — JavaScript or TypeScript

**Source**: [frontend-patterns.md STD-FE-008](frontend-patterns.md)

**Question**: The frontend is plain JavaScript. Adopt TypeScript, or stay on JS with JSDoc?

**Impact**:
- 🔤 **Generated API models**: `openapi-typescript` produces `.d.ts`. Without TypeScript, backend DTO changes are not caught anywhere on the frontend
- 🧰 **Editor support**: type-aware completion and rename-refactoring across the codebase
- 🧹 **Lint depth**: oxlint's type-aware rules require TypeScript
- ⏱️ **Cost**: a rename plus incremental annotation; Vite compiles `.tsx` with no config change

**Options**:

| Option | Pros | Cons | Best for |
|--------|------|------|----------|
| **Adopt TypeScript** | Generated API types become useful; a renamed backend field becomes a compile error; better refactoring | Upfront conversion; type-level learning curve | Anything that will grow past a few pages |
| **Stay on JS + JSDoc `@typedef`** | No migration; editors still give completion from JSDoc | JSDoc types are advisory — nothing fails a build; no generated models | A deliberately small app |
| **Allow both (`allowJs`)** | Incremental adoption, file by file | Two conventions coexisting indefinitely; the boundary is where bugs collect | A large existing JS codebase |

**Note**: `@types/react` and `@types/react-dom` are **already** in `devDependencies`, so the type definitions are on disk and doing nothing. That is a small signal that TypeScript was anticipated.

**Who decides**: whoever will maintain the frontend
**When**: before the first real page — converting three files is trivial, converting thirty is a project

---

## Decision 4: Frontend Component Library

**Source**: [frontend-patterns.md STD-FE-001](frontend-patterns.md)

**Question**: There is no component library and no design system — just `App.css` and `index.css`. What provides buttons, inputs, dialogs, tables, and the accessibility baseline?

**Impact**:
- ♿ **Accessibility**: focus management, ARIA wiring, and keyboard interaction in a dialog or combobox are genuinely hard to get right by hand
- 📊 **Data grids**: sorting, pagination, column management, and keyboard navigation are weeks of work to build and a dependency to adopt
- 📦 **Bundle size**: a full component library is a significant addition
- 🎨 **Design control**: more library styling means less bespoke design

**Options**:

| Option | Pros | Cons | Best for |
|--------|------|------|----------|
| **None — semantic HTML + CSS** | Zero dependencies; full control; smallest bundle | Every dialog, combobox, and grid is hand-built, including its a11y | Simple, form-light apps |
| **A full library (MUI, Mantine)** | Comprehensive components; a11y handled; a data grid included | Large bundle; opinionated styling; a big API surface to learn | Data-dense internal apps |
| **Headless (Radix, React Aria) + own CSS** | Correct a11y behaviour, full visual control, small footprint | No visual design provided; no data grid | Custom design systems |

**Coupled to Decision 3** — most libraries are markedly better in TypeScript, where component props are checked.

**Also gates [text-truncation-pattern.md](text-truncation-pattern.md)**, whose grid-integration section assumes a grid component exists.

**Who decides**: frontend maintainer + whoever owns visual design
**When**: before the first page containing a form or a table

---

## Decision 5: Authentication Strategy

**Source**: [architecture.md STD-ARC-006](architecture.md), [database-patterns.md STD-DB-006](database-patterns.md)

**Question**: Nothing is authenticated. `/api/hello`, all seven `/camel/*` routes, and — most seriously — the H2 console at `/h2-console` are open. What secures them?

**Impact**:
- 🔒 **Exposure**: `/h2-console` is an unauthenticated SQL shell over the entire database
- 🧪 **Test harness**: adding Spring Security breaks every `@WebMvcTest` until the test config accounts for it
- 🔗 **Camel routes**: `platform-http` endpoints are covered by the servlet filter chain, so a `SecurityFilterChain` protects them too — but the matchers must be written to include `/camel/**`
- 🖥️ **Frontend**: a token-based scheme needs a login flow, token storage, and refresh handling

**Options**:

| Option | User impact | Security | Implementation | Suitable for |
|--------|-------------|----------|----------------|--------------|
| **Nothing (current)** | None | ❌ Fully open, including a SQL console | — | Local learning only, never deployed |
| **Profile-gate the dev endpoints only** | None | ⚠️ Partial — closes the console, leaves the API open | Move H2 console + `show-sql` behind a `dev` profile | The minimum before any deploy |
| **OAuth2 Resource Server (JWT)** | Login flow required | ✅ Stateless, standard, no session store | `spring-boot-starter-oauth2-resource-server` + an identity provider | An API with a separate SPA |
| **Session + form login** | Login page | ✅ Simple, well-trodden | `spring-boot-starter-security` defaults | A server-rendered app |

**Recommendation to discuss**: do the profile-gating (Option 2) *now* regardless of the longer-term answer — it is a ten-minute change that closes the worst exposure, and it is a prerequisite for every other option.

**Who decides**: whoever owns the deployment + backend lead
**When**: Option 2 immediately; the full strategy before any deployment

---

## Decision 6: Camel vs. Spring MVC Boundary

**Source**: [integration-guide.md STD-INT-001](integration-guide.md)

**Question**: Two HTTP stacks are running in the same JVM on the same port — Spring MVC on `/api/*` and Camel on `/camel/*`. Which traffic belongs to which?

**Impact**:
- 🧭 **Predictability**: a contributor needs to know where to add an endpoint without asking
- 🧪 **Testability**: Camel routes test via `ProducerTemplate`; controllers test via `MockMvc`. Two harnesses, two skill sets
- 👀 **Observability**: `@RestControllerAdvice` does not see Camel exceptions, and Camel's `onException` does not see controller exceptions. Error handling is duplicated by construction (STD-INT-003)
- 📖 **OpenAPI**: springdoc documents controllers; `camel-openapi-java` documents routes. Two specs unless deliberately merged

**Options**:

| Option | Pros | Cons | Best for |
|--------|------|------|----------|
| **Camel for integration only** (current de facto) | Each tool used for its strength; CRUD keeps Spring MVC's validation, content negotiation, and OpenAPI | Two stacks to understand; error handling written twice | Most cases — the documented default |
| **Camel for everything** | One error-handling model; uniform routes; EIP available everywhere | Loses Bean Validation, `@ModelAttribute` binding, and springdoc; a Camel route is a poor place for CRUD | Integration-heavy products |
| **Spring MVC for everything; drop Camel** | One stack; simplest onboarding | Loses declarative retry, backoff, and content-based routing — the working `RetryRoute` behaviour would be rebuilt by hand | Apps with no real integration needs |

**The current split is the recommended one** and is what [integration-guide.md STD-INT-001](integration-guide.md) documents. What is genuinely undecided is the rule for the edge cases: an endpoint that reads the database *and* calls an upstream service could reasonably go either way.

**Who decides**: backend architecture
**When**: before the second integration, while the precedent is still cheap to set

---

## Decision 7: CI and Quality Gates

**Source**: [source-repos.md — What is deliberately absent](source-repos.md), [testing-guide.md STD-TST-012](testing-guide.md)

**Question**: There is no CI. Nothing runs `./mvnw test` or `npm run lint` automatically, and no coverage is measured.

**Impact**:
- ✅ **Regression safety**: the three existing Camel tests only protect what someone remembers to run
- 📊 **Coverage targets**: [testing-guide.md STD-TST-012](testing-guide.md) sets targets that nothing measures — JaCoCo is not configured
- 🚦 **Standards enforcement**: every "MUST" in these docs is currently honour-system

**Options**:

| Option | Pros | Cons |
|--------|------|------|
| **GitHub Actions** | Free for public repos; simple YAML; matrix builds | Vendor-specific config |
| **Pre-commit hooks only** | No infrastructure; instant feedback | Bypassable with `--no-verify`; does not run on someone else's machine |
| **Nothing (current)** | No setup | Every standard is unenforced |

**Minimum worth having**: one workflow running `./mvnw test` in `backend/` and `npm run lint && npm run build` in `frontend/` on every push. That is roughly twenty lines and it turns the documented standards into checked ones.

**Enforcement that could be added mechanically rather than documented**:
- An ArchUnit test asserting the STD-LAY-006 dependency rules
- JaCoCo with the STD-TST-012 thresholds
- `spotless` or `checkstyle` for formatting

**Who decides**: whole team
**When**: before there is more than one contributor

---

## How to Incorporate These into a Plan

### In a feature plan (`plan.md`)

```markdown
## Technical Decisions Pending

The following architectural decisions should be resolved before or during this
feature's design phase. See
[docs/architecture/TECHNICAL_DECISIONS_PENDING.md](../../docs/architecture/TECHNICAL_DECISIONS_PENDING.md).

- [ ] **Production datasource** (Decision 1) — Owner: Backend + DevOps
- [ ] **Schema management** (Decision 2) — Owner: Backend lead
- [ ] **Frontend language** (Decision 3) — Owner: Frontend
```

### In a spec (`spec.md`)

```markdown
## Design Decisions Needed

Before implementation can begin, the team should finalize:

1. **Schema management** — Flyway or `ddl-auto`? This feature adds the first
   persistent table, so it forces the answer.
   See [TECHNICAL_DECISIONS_PENDING.md — Decision 2](../../docs/architecture/TECHNICAL_DECISIONS_PENDING.md)
```

### As a tracker issue

```
Title: [DECISION] Schema management — Flyway vs ddl-auto

The app currently uses spring.jpa.hibernate.ddl-auto=update against in-memory
H2. The <feature> feature adds the first table whose data must survive a
restart, which makes this unsafe.

Options: Flyway (+ ddl-auto=validate), Liquibase, or keep ddl-auto=update.

See: docs/architecture/TECHNICAL_DECISIONS_PENDING.md — Decision 2

Owner: Backend lead
Timeline: before the <feature> implementation phase
```

---

## Decision Status Tracking

| # | Decision | Status | Decided by | Date | Outcome |
|---|----------|--------|-----------|------|---------|
| 1 | Production datasource | ⏳ Pending | — | — | — |
| 2 | Schema management | ⏳ Pending | — | — | — |
| 3 | Frontend language | ⏳ Pending | — | — | — |
| 4 | Frontend component library | ⏳ Pending | — | — | — |
| 5 | Authentication strategy | ⏳ Pending | — | — | — |
| 6 | Camel vs. Spring MVC boundary | ⏳ Pending | — | — | — |
| 7 | CI and quality gates | ⏳ Pending | — | — | — |

---

## Key Principles for This Document

1. **Not making decisions** — this lists *what* needs deciding and *who* should decide it
2. **Not prescriptive** — multiple valid options are presented; the team chooses
3. **Living document** — update the status table as decisions are made, and clear the corresponding `GAP-` marker in the architecture doc that owns it
4. **Cross-referenced** — every decision links to the standard it blocks

## When a Decision Is Made

1. Fill in the row in **Decision Status Tracking** above
2. Update the standard that was blocked — replace the `GAP-` callout with the implemented behaviour and raise its **Confidence** rating
3. Update the Stack Summary in [README.md](README.md)
4. Update the row in **Quick Reference** at the top of this file
