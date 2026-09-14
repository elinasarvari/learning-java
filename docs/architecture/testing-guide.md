# Testing Guide

**Repos**: `backend/` + `frontend/` | **Status**: Backend Camel tests implementation-validated; everything else prescriptive

This guide documents the testing patterns for learning-java. The three existing backend test classes are cited as live examples; frontend and slice-test patterns are prescriptive.

---

## Testing Stack

| Layer | Framework | Tools | Status |
|-------|-----------|-------|--------|
| Backend unit tests | JUnit 5 | Mockito, AssertJ (via `spring-boot-starter-test`) | ✅ available, no unit tests yet |
| Backend Camel route tests | JUnit 5 | `camel-test-spring-junit5`, `AdviceWith`, `MockEndpoint` | ✅ implemented |
| Backend web slice tests | JUnit 5 | `@WebMvcTest` + MockMvc | ❌ prescribed |
| Backend persistence tests | JUnit 5 | `@DataJpaTest` | ❌ prescribed |
| Frontend unit/component | Vitest | React Testing Library, `@testing-library/jest-dom` | ❌ not installed |
| Frontend E2E | Playwright | — | ❌ not installed |

`spring-boot-starter-test` already brings JUnit Jupiter, Mockito, AssertJ, Hamcrest, JSONassert, and `spring-test`. No additional test dependency is needed for anything in the backend sections below.

**Running tests**:

```bash
cd backend && ./mvnw test          # all backend tests
cd frontend && npm test            # once Vitest is installed
```

---

## Testing Philosophy

1. **Test behavior, not implementation** — assert on public outputs, not internal state
2. **AAA pattern** — every test follows Arrange / Act / Assert
3. **Isolated tests** — no shared mutable state between tests (see the `FlakyService` caveat in STD-TST-013)
4. **Clear names** — `methodName_should_expectedBehavior`
5. **Fast unit tests** — service tests run with no Spring context, no database, no network

**The Spring-context cost is the main thing to budget.** A plain JUnit + Mockito test runs in milliseconds; a `@SpringBootTest` starts the whole application. Reach for the narrowest harness that can express the assertion:

```
plain JUnit + Mockito   →  @DataJpaTest / @WebMvcTest  →  @SpringBootTest
        fastest                    slice                      slowest
```

---

## Backend: Unit Tests

### STD-TST-001 — Service Layer Unit Tests

**Normative Rule**: Business services MUST be covered by plain JUnit 5 tests with all collaborators mocked via Mockito. These tests MUST NOT start a Spring context, touch a database, or make a network call. Tests MUST follow AAA and use `methodName_should_expectedBehavior` naming.

**Location**: `backend/src/test/java/com/example/app/service/<feature>/`

**Setup pattern** — construct the class under test directly. Because STD-DI-002 mandates constructor injection, no Spring, no `@InjectMocks`, and no reflection is needed:

```java
@ExtendWith(MockitoExtension.class)
class assetServiceImplTest {

    @Mock private assetRepository assetRepository;
    @Mock private assetMapper mapper;

    private assetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new assetServiceImpl(assetRepository, mapper);
    }
}
```

**Test naming and structure**:

```java
@Test
void findProfileById_should_returnMappedDomain_whenassetExists() {
    // Arrange
    var entity = new assetEntity();
    entity.setassetName("Test asset");
    var domain = new assetProfileDomain(1L, "Test asset");

    when(assetRepository.findById(1L)).thenReturn(Optional.of(entity));
    when(mapper.toProfileDomain(entity)).thenReturn(domain);

    // Act
    Optional<assetProfileDomain> result = service.findProfileById(1L);

    // Assert
    assertThat(result).contains(domain);
    verify(assetRepository).findById(1L);
}

@Test
void findProfileById_should_returnEmpty_whenassetMissing() {
    // Arrange
    when(assetRepository.findById(99L)).thenReturn(Optional.empty());

    // Act
    Optional<assetProfileDomain> result = service.findProfileById(99L);

    // Assert
    assertThat(result).isEmpty();
    verifyNoInteractions(mapper);
}

@Test
void create_should_throwConflict_whenNameAlreadyExists() {
    // Arrange
    var domain = new assetDomain(null, "Duplicate Co");
    when(assetRepository.existsByNameIgnoreCase("Duplicate Co")).thenReturn(true);

    // Act + Assert
    assertThatThrownBy(() -> service.create(domain))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Duplicate Co");

    verify(assetRepository, never()).save(any());
}
```

**Guidelines**:
- Mock every collaborator (repository, mapper, external client)
- Prefer specific argument values over `any()` — `any()` hides the case where the wrong id was passed
- Use AssertJ (`assertThat`), not JUnit's `Assertions.assertEquals` — the fluent API gives far better failure messages, especially on collections and `Optional`
- `verify(...)` only interactions the test is genuinely about; over-verification makes tests brittle
- `verifyNoInteractions(...)` is a real assertion — it proves the short-circuit path was taken
- Never use a real database, HTTP client, or file system

**Anti-Patterns**:
- ❌ `@SpringBootTest` for a service unit test — it starts the whole application to test one method
- ❌ `@MockBean` in a plain unit test (`@MockBean` requires a Spring context and is deprecated in favour of `@MockitoBean` in Boot 3.4+)
- ❌ Mocking the class under test
- ❌ `when(...)` stubs that no assertion depends on — Mockito's strict stubs will fail the test, which is the correct behaviour

**Confidence**: Medium (prescriptive)

---

### STD-TST-002 — Mapper Tests

**Normative Rule**: Each MapStruct mapper MUST have a test constructing the **generated** implementation directly and asserting the mapping output. The mapper MUST NOT be mocked in its own test.

```java
class assetMapperTest {

    // Mappers.getMapper(...) loads the generated assetMapperImpl — no Spring needed.
    private final assetMapper mapper = Mappers.getMapper(assetMapper.class);

    @Test
    void toDomain_should_mapAllFields() {
        // Arrange
        var entity = new assetEntity();
        entity.setId(1L);
        entity.setassetName("Acme");

        // Act
        assetDomain domain = mapper.toDomain(entity);

        // Assert
        assertThat(domain.id()).isEqualTo(1L);
        assertThat(domain.name()).isEqualTo("Acme");
    }
}
```

> With `unmappedTargetPolicy = ReportingPolicy.ERROR` (mandatory per [backend-patterns.md STD-BE-005](backend-patterns.md)), an unmapped field is already a **compile error**. That makes this test less critical than it would otherwise be — its real job is verifying deliberate renames and type conversions, not field coverage.

**Confidence**: Low (prescriptive — no mapper exists)

---

## Backend: Slice Tests

### STD-TST-003 — Web Layer Tests (`@WebMvcTest`)

**Normative Rule**: Controllers MUST be tested with `@WebMvcTest`, loading only the web layer with the service mocked. Tests MUST assert the HTTP status, the response envelope shape, and the fact that validation rejects a malformed body.

```java
@WebMvcTest(assetController.class)
class assetControllerTest {

    @Autowired private MockMvc mockMvc;

    // Boot 3.4+: @MockitoBean. On Boot 3.3.2 this is @MockBean.
    @MockBean private assetService assetService;
    @MockBean private assetWebMapper mapper;

    @Test
    void getassetProfile_should_return200WithEnvelope_whenFound() throws Exception {
        when(assetService.findProfileById(1L))
            .thenReturn(Optional.of(new assetProfileDomain(1L, "Acme")));
        when(mapper.toProfileResponse(any()))
            .thenReturn(new assetProfileResponse(1L, "Acme"));

        mockMvc.perform(get("/api/v1/assets/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Acme"))
            .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void getassetProfile_should_return404WithError_whenMissing() throws Exception {
        when(assetService.findProfileById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/assets/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.errors[0].code").value(404));
    }

    @Test
    void createasset_should_return400_whenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isNotEmpty());
    }
}
```

Two notes:
- `@WebMvcTest` picks up `@RestControllerAdvice` beans, so the envelope produced by the global handler (STD-BE-009) is genuinely exercised — that is why the 404 and 400 assertions are meaningful.
- The third test is the reason `@Valid` coverage belongs here rather than in a service test: it verifies the annotation is actually wired, not just present.

**Confidence**: Low (prescriptive)

---

### STD-TST-004 — Persistence Tests (`@DataJpaTest`)

**Normative Rule**: Non-trivial repository queries — `Specification` compositions, `@Query` JPQL, derived queries with unusual keywords — MUST be covered by a `@DataJpaTest`. Methods generated by Spring Data with no custom logic (`save`, `findById`) MUST NOT be tested; that tests the framework.

```java
@DataJpaTest
class assetRepositoryTest {

    @Autowired private assetRepository assetRepository;
    @Autowired private TestEntityManager em;

    @Test
    void findAll_should_filterByNameFragment_whenSpecificationApplied() {
        // Arrange
        em.persist(asset("Acme Insurance"));
        em.persist(asset("Beta Mutual"));
        em.flush();

        // Act
        var results = assetRepository.findAll(assetSpecifications.nameContains("acme"));

        // Assert
        assertThat(results).extracting(assetEntity::getassetName)
            .containsExactly("Acme Insurance");
    }

    @Test
    void findById_should_excludeSoftDeletedRows() {
        var deleted = asset("Gone Corp");
        deleted.setDeleted(true);
        em.persist(deleted);
        em.flush();
        em.clear();                       // force a DB round trip, not a first-level cache hit

        assertThat(assetRepository.findById(deleted.getId())).isEmpty();
    }
}
```

`@DataJpaTest` is transactional and rolls back after each test, and it auto-configures an embedded database — which here is the same H2 the app already uses.

`em.clear()` before a read assertion matters more than it looks: without it, the entity is still in the persistence context and is returned from the first-level cache without a query, so a `@SQLRestriction` test would pass for the wrong reason.

**Caveat on database parity**: these tests run against H2. If production becomes PostgreSQL (Decision 1), a query using database-specific SQL will pass here and fail there. At that point, switch to Testcontainers so the tests run against the real engine.

**Confidence**: Low (prescriptive)

---

## Backend: Camel Route Tests (Implemented)

### STD-TST-013 — Routes That Need No Network

**Normative Rule**: Camel routes with no external dependency MUST be tested with `@CamelSpringBootTest` and a `ProducerTemplate` sending directly to the route's `direct:` endpoint. `@SpringBootTest(webEnvironment = RANDOM_PORT)` is REQUIRED — the default `MOCK` environment does not start a servlet container.

```java
// backend/src/test/java/com/example/app/camel/CamelRoutesTest.java
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
```

**Four things this test class teaches**:

1. **`RANDOM_PORT` is not optional.** The `rest(...)` declarations bind to `platform-http`, which needs a real servlet container. Under `WebEnvironment.MOCK` the REST routes fail to start, and that failure aborts the whole `CamelContext` — taking down the `direct:` routes the test actually wanted.
2. **Send to `direct:`, not over HTTP.** Testing the internal endpoint keeps the test fast and independent of the HTTP contract. That is the payoff of the contract/implementation split (STD-INT-001).
3. **Headers, not body, for path parameters** — `requestBodyAndHeader(..., "name", "Elina", ...)` mirrors how the route actually receives them.
4. **`flakyRouteSucceedsAfterRedeliveries` is deliberately written to tolerate any starting counter value.** `FlakyService` holds shared singleton state (VAR-DI-001) that persists across tests in the same context. Asserting "succeeds on attempt 3 exactly" would make the test order-dependent. Asserting "succeeds within the configured retries" does not.

**Anti-Patterns**:
- ❌ Omitting `webEnvironment = RANDOM_PORT` on a context containing `rest()` routes
- ❌ Testing a route by issuing an HTTP request to `/camel/...` when the `direct:` endpoint would do
- ❌ Writing an assertion that depends on where a shared counter starts

**Traceability**: `backend/src/test/java/com/example/app/camel/CamelRoutesTest.java`

**Confidence**: High

---

### STD-TST-014 — Routes That Call External Systems

**Normative Rule**: A route that calls an external system MUST be tested with the outbound step replaced by a `MockEndpoint` using `AdviceWith`. The test class MUST carry `@UseAdviceWith` and `@DirtiesContext`. The step MUST be matched by **id**, never by URI.

```java
// backend/src/test/java/com/example/app/camel/GithubRouteMockTest.java
@CamelSpringBootTest
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
```

**Why each annotation is there**:

| Annotation | Reason |
|---|---|
| `@UseAdviceWith` | Stops Camel auto-starting, so the route can be rewritten before it runs. **You must then call `context.start()` yourself** — forgetting this produces a test that hangs or times out. |
| `@DirtiesContext` | The rewritten route is a permanent mutation of that `CamelContext`. Without this, the next test class inheriting the cached context gets a route pointing at `mock:github`. |
| `@CamelSpringBootTest` | Wires the Camel test support into the Spring Boot context. |
| `RANDOM_PORT` | Same `platform-http` reason as STD-TST-013. |

**Match by id, never by URI.** `weaveByToUri("{{github.base-url}}/...")` finds nothing, because at advice time the URI is still the unresolved placeholder — Camel only resolves `{{...}}` when the endpoint is created. This is precisely why STD-INT-006 mandates `.id("call-github")` on the producer step.

**`assertIsSatisfied()` is the assertion that matters.** Checking the response body alone would still pass if the route silently skipped the upstream call. `expectedMessageCount(1)` + `assertIsSatisfied()` proves the call happened.

**Anti-Patterns**:
- ❌ `weaveByToUri` against a URI containing a `{{placeholder}}`
- ❌ `@UseAdviceWith` without a `context.start()`
- ❌ `AdviceWith` without `@DirtiesContext` — leaks a rewritten route into other test classes
- ❌ Letting a test hit the real GitHub API — slow, rate-limited, and offline-hostile

**Traceability**: `backend/src/test/java/com/example/app/camel/GithubRouteMockTest.java`

**Confidence**: High

---

### STD-TST-015 — Context Smoke Test

**Normative Rule**: The repository MUST retain a single `@SpringBootTest` that only asserts the context loads. It MUST NOT accumulate additional assertions.

```java
// backend/src/test/java/com/example/app/ApplicationTests.java
@SpringBootTest
class ApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

An empty test body looks pointless and is not: it catches a missing bean, a circular dependency, a malformed property, a duplicate bean name, and — because `camel-spring-boot-starter` builds every route at startup — a broken route URI. It is the cheapest wiring regression test available.

Do not add assertions to it. Any specific behaviour belongs in a narrower test.

**Traceability**: `backend/src/test/java/com/example/app/ApplicationTests.java`

**Confidence**: High

---

## Frontend: Unit / Component Tests

### STD-TST-005 — Vitest Setup

**Normative Rule**: Frontend tests MUST use Vitest, not Jest. Test files MUST be co-located with the component as `<component>.test.jsx`.

**Rationale**: Vite already owns the module graph, the JSX transform, and the alias configuration. Vitest reuses `vite.config.js` directly, so there is exactly one build configuration. Jest would need a parallel transform pipeline (Babel or SWC) kept manually in sync with Vite's — a known source of "works in dev, fails in test" drift.

**Prescribed setup**:

```bash
npm i -D vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event
```

```js
// vite.config.js
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
  },
})
```

```js
// src/test/setup.js
import '@testing-library/jest-dom/vitest'
```

```json
"scripts": {
  "test": "vitest run",
  "test:watch": "vitest"
}
```

**Confidence**: Low (prescriptive — not installed)

---

### STD-TST-006 — `renderWithProviders` Helper (Required)

**Normative Rule**: ALL React component tests MUST wrap the component in `renderWithProviders()`. A component MUST NEVER be rendered without the full provider stack.

```jsx
// src/test/render-with-providers.jsx
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'

export function renderWithProviders(ui, { route = '/' } = {}) {
  // A fresh client per test — a shared client leaks cached data between tests.
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}
```

`retry: false` is essential. TanStack Query's default retry means an error-path test waits through several backoff delays before `isError` becomes true, and then usually times out.

A **fresh `QueryClient` per test** is equally essential — a module-level client caches results across tests, so test B sees test A's data and passes for the wrong reason.

**Confidence**: Low (prescriptive)

---

### STD-TST-007 — Query Hook Mocking

**Normative Rule**: TanStack Query hooks MUST be mocked in component tests with `vi.mock()` on the hook module. Components MUST NOT perform real HTTP calls in tests.

```jsx
import { vi, describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/render-with-providers'
import * as assetQueries from '../../api/hooks/useassetQueries'
import assetProfile from './asset-profile'

vi.mock('../../api/hooks/useassetQueries')

describe('assetProfile', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should render the asset name when the query succeeds', () => {
    vi.spyOn(assetQueries, 'useasset').mockReturnValue({
      data: { id: 1, name: 'Acme' },
      isLoading: false,
      isError: false,
    })

    renderWithProviders(<assetProfile />)

    expect(screen.getByText('Acme')).toBeInTheDocument()
  })

  it('should show a not-found message when the API returns 404', () => {
    vi.spyOn(assetQueries, 'useasset').mockReturnValue({
      isLoading: false,
      isError: true,
      error: { status: 404 },
    })

    renderWithProviders(<assetProfile />)

    expect(screen.getByRole('alert')).toHaveTextContent(/does not exist/i)
  })
})
```

**Alternative — MSW.** For tests that should exercise the real hook and client (including envelope unwrapping and error decoration), intercept at the network layer with Mock Service Worker instead of mocking the hook. Prefer MSW for integration-style tests of the `api/` layer itself; prefer hook mocking for page-level rendering tests, where the HTTP round trip is noise.

**Router mocking**:

```jsx
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal()),
  useParams: () => ({ id: '123' }),
  useNavigate: () => mockNavigate,
}))
```

`importOriginal` must be awaited — Vitest's factory is async, unlike Jest's `requireActual`.

**Confidence**: Low (prescriptive)

---

### STD-TST-008 — Query Priority and `data-testid`

**Normative Rule**: Assertions MUST prefer `screen.getByRole()`, fall back to `screen.getByTestId()` for interactive elements without a distinguishing accessible name, and use `screen.getByText()` only as a last resort. All interactive elements MUST carry a `data-testid`.

```jsx
<button data-testid="go-back-to-summary-button" onClick={handleGoBack}>
  Go back to summary
</button>
```

```jsx
// preferred — also asserts the element is reachable by assistive technology
screen.getByRole('button', { name: /go back to summary/i })

// acceptable when the accessible name is ambiguous or dynamic
screen.getByTestId('go-back-to-summary-button')
```

`getByRole` is first for a reason: it fails when a `<div onClick>` is used instead of a `<button>`, when a form control has no label, or when an icon-only button has no accessible name — so the test doubles as an accessibility check. `data-testid` will happily pass on all three.

**Confidence**: Low (prescriptive)

---

### STD-TST-009 — Frontend Test Naming

**Normative Rule**: Frontend tests MUST use `it('should [action] when [condition]', …)` inside a `describe('ComponentName', …)` block.

```jsx
describe('ComponentName', () => {
  it('should render the summary when data loads', () => {})
  it('should navigate to the list when the back button is clicked', async () => {})
  it('should show an error when the request fails', () => {})
})
```

---

## STD-TST-010 — Frontend E2E Tests (Playwright)

**Normative Rule** (prescribed): Critical user workflows MUST be covered by Playwright tests under `frontend/e2e/`, with the config at `frontend/playwright.config.js`.

**Mock vs. real API**:
- Use `page.route()` to stub the API for anything asserting on UI display, navigation, error states, or empty states — this keeps assertions deterministic and independent of seed data volume.
- Use the real API only when asserting on behaviour that depends on actual persisted data (sort order across pages, pagination boundaries).

**Seed data**: seed the minimum required. Named purpose-built records with predictable field values beat large generated datasets — they make a failure diagnosable.

**Confidence**: Low (prescriptive — not installed)

---

## STD-TST-011 — Mocking Guidelines

**Normative Rule**: Tests MUST mock all external dependencies. Tests MUST NOT mock the class or function under test, value objects, or plain data records.

**MUST mock**:
- Repository interfaces in service unit tests
- `RestClient` / external HTTP in service unit tests
- The outbound step of a Camel route (via `AdviceWith` + `MockEndpoint`)
- Query hooks in React component tests
- `useNavigate` / `useParams` in React page tests

**MUST NOT mock**:
- The class or component under test
- Java `record`s and plain domain objects — construct them
- MapStruct mappers in their own test (STD-TST-002)
- The `CamelContext` — use the real one with `AdviceWith`

**Why not mock records**: a mocked record returns `null` from every accessor by default, so the test passes for reasons unrelated to the mapping or logic it claims to verify. Constructing a real one is shorter anyway.

---

## STD-TST-012 — Code Coverage Goals

**Normative Rule**: Coverage MUST meet the minimum targets below. Generated code, plain data carriers, and framework code MUST be excluded from measurement.

| Area | Target | Notes |
|------|--------|-------|
| Backend service layer | 80–90% | Unit tests + edge cases |
| Backend repository layer | 60–70% | `@DataJpaTest` on non-trivial queries only |
| Backend controllers | 70–80% | `@WebMvcTest` slices |
| Backend Camel routes | Key routes | Happy path + the error/fallback branch |
| Frontend components | 60–70% | Focus on behaviour |
| Frontend E2E | Key flows | Happy paths + critical errors |

**Do NOT write tests for**:
- Generated code (MapStruct `*Impl`, generated API types)
- Java `record`s and DTOs with no logic
- Spring Data methods with no custom logic (`save`, `findById`)
- Framework code

Coverage is a smoke detector, not a goal. A service at 85% with the error branches untested is worse than one at 70% that covers them.

> JaCoCo is not configured. Add `jacoco-maven-plugin` when coverage is first enforced in CI.

---

## STD-TST-016 — Test Organization

**Normative Rule**: Backend test classes MUST mirror the main-source package of the class under test. Frontend test files MUST be co-located with the component using the `.test.jsx` extension.

```text
backend/src/test/java/com/example/app/
├── ApplicationTests.java                    # context smoke test — STD-TST-015
├── camel/
│   ├── CamelRoutesTest.java                 # STD-TST-013 ✅
│   └── GithubRouteMockTest.java             # STD-TST-014 ✅
├── web/<feature>/<Feature>ControllerTest.java     # (prescribed) @WebMvcTest
├── service/<feature>/<Feature>ServiceImplTest.java # (prescribed) plain JUnit + Mockito
│                     <Feature>MapperTest.java
└── repository/<feature>/<Feature>RepositoryTest.java # (prescribed) @DataJpaTest

frontend/src/
├── pages/<feature>/<feature>.jsx
├── pages/<feature>/<feature>.test.jsx       # co-located
├── api/services/<feature>.service.test.js
└── test/
    ├── setup.js
    └── render-with-providers.jsx
```

---

## Quick Reference: Before Writing a Test

**Backend service test**:
1. `@ExtendWith(MockitoExtension.class)`, `@Mock` each collaborator
2. Construct the class under test in `@BeforeEach` — no Spring context
3. Name it `methodName_should_expectedBehavior`
4. `// Arrange` → `// Act` → `// Assert`, using AssertJ
5. No database, no network, no file system

**Backend Camel route test**:
1. `@CamelSpringBootTest` + `@SpringBootTest(webEnvironment = RANDOM_PORT)` — always `RANDOM_PORT`
2. Send to the `direct:` endpoint with `ProducerTemplate`, not over HTTP
3. If the route calls out: add `@UseAdviceWith` + `@DirtiesContext`, `weaveById(...)` the step, and **call `context.start()`**
4. Assert with `mock.assertIsSatisfied()`, not just on the body

**Frontend component test**:
1. `vi.mock()` the query hook module at file top
2. `vi.spyOn(hooks, 'useXxx').mockReturnValue({...})` per scenario
3. `renderWithProviders(<Component />)` — fresh `QueryClient`, `retry: false`
4. Assert with `screen.getByRole()` first, `getByTestId()` second
5. `vi.clearAllMocks()` in `beforeEach`
