# Backend Patterns

**Repo**: `backend/` | **Stack**: Java 21 / Spring Boot 3.3.2 / Maven | **Status**: Camel layer implementation-validated; layered stack prescriptive

---

## Technical Context

| Item | Value | Status |
|------|-------|--------|
| **Language** | Java 21 | ✅ |
| **Framework** | Spring Boot 3.3.2 (spring-boot-starter-web, servlet stack) | ✅ |
| **Build** | Maven, wrapper pinned to 3.9.16 | ✅ |
| **ORM / Data Access** | Spring Data JPA + Hibernate 6 (H2 in-memory, dev) | ✅ configured, unused |
| **Integration** | Apache Camel 4.8.5 (`camel-spring-boot-bom`) | ✅ |
| **Caching** | Redis 7 / Lettuce via `spring-boot-starter-data-redis`; custom `RedisCache`, no `@Cacheable` | ✅ |
| **Validation** | Jakarta Bean Validation (`spring-boot-starter-validation`) | ✅ on classpath, unused |
| **Mapping** | MapStruct | ❌ prescribed, not on classpath |
| **Logging** | SLF4J + Logback (Spring Boot default) | ✅ defaults only |
| **Boilerplate** | Lombok (`optional`, excluded from the boot jar) | ✅ |
| **Authentication** | — | ❌ GAP-ARC-006 |
| **Configuration** | `application.properties` + environment variables | ✅ |
| **HTTP Resilience** | Camel `onException` + `maximumRedeliveries` | ✅ |
| **Testing** | JUnit 5, `spring-boot-starter-test`, `camel-test-spring-junit5` | ✅ |
| **Dev tooling** | `spring-boot-devtools` (runtime, optional) | ✅ |
| **Target Platform** | Local JVM; no container or deployment target defined yet | — |
| **Module** | Single Maven module `com.example:app:0.0.1-SNAPSHOT` | ✅ |

> **Camel version coupling**: `camel.version` is pinned to `4.8.5` — the LTS line targeting Spring Boot 3.3.x. Bumping either version alone fails at startup. Check the Camel Spring Boot compatibility matrix before changing `spring-boot-starter-parent` or `camel.version`.

---

## Project Structure

Layer-first, feature-second. Packages marked *(prescribed)* do not exist yet.

> The `catalog` feature is a complete vertical slice through `web/` → `service/` → `repository/`, added as the worked example for [caching-pattern.md](caching-pattern.md). It is the reference to copy for a new feature.

```text
backend/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/
└── src/
    ├── main/
    │   ├── java/com/example/app/
    │   │   ├── Application.java                # @SpringBootApplication — anchors component scan
    │   │   ├── HelloController.java            # legacy scaffold; move to web/hello/ (VAR-LAY-002)
    │   │   ├── camel/                          # INTEGRATION LAYER
    │   │   │   ├── HelloRoute.java             #   inbound consumer endpoints
    │   │   │   ├── GithubRoute.java            #   outbound producer + error mapping
    │   │   │   ├── RetryRoute.java             #   redelivery + fallback
    │   │   │   └── FlakyService.java           #   deterministic unreliable-upstream stand-in
    │   │   ├── cache/                          # CROSS-CUTTING INFRASTRUCTURE
    │   │   │   ├── RedisCache.java             #   the only class that talks to Redis
    │   │   │   ├── CacheKeys.java              #   every cache key shape
    │   │   │   └── CacheProperties.java        #   @ConfigurationProperties("app.cache")
    │   │   ├── config/                         # @Configuration classes
    │   │   │   ├── CorsConfig.java             #   STD-ARC-007
    │   │   │   └── <Feature>Config.java        #   STD-DI-001
    │   │   ├── web/                            # PRESENTATION LAYER (catalog/ is real)
    │   │   │   ├── common/
    │   │   │   │   ├── BaseController.java     #   response helpers — STD-BE-002
    │   │   │   │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice — STD-BE-009
    │   │   │   │   ├── CorrelationIdFilter.java     # MDC enrichment — STD-BE-007
    │   │   │   │   └── dto/{ApiResponse,ApiError,SearchResults}.java
    │   │   │   └── <feature>/
    │   │   │       ├── <Feature>Controller.java
    │   │   │       ├── <Feature>WebMapper.java #   MapStruct: request/response ↔ domain
    │   │   │       └── dto/                    #   records: Create…Request, …Response
    │   │   ├── service/                        # BUSINESS LAYER (catalog/ is real)
    │   │   │   ├── common/ApiException.java    #   typed exception carrying an HttpStatus
    │   │   │   └── <feature>/
    │   │   │       ├── <Feature>Service.java   #   interface
    │   │   │       ├── <Feature>ServiceImpl.java
    │   │   │       ├── <Feature>Mapper.java    #   MapStruct: entity ↔ domain
    │   │   │       ├── domain/                 #   records
    │   │   │       └── enums/                  #   STD-BE-011
    │   │   └── repository/                     # DATA ACCESS LAYER (catalog/ is real)
    │   │       └── <feature>/
    │   │           ├── <Feature>Repository.java   # extends JpaRepository + JpaSpecificationExecutor
    │   │           ├── entity/<Feature>Entity.java
    │   │           └── spec/<Feature>Specifications.java
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/                   # (prescribed) Flyway — STD-DB-004
    └── test/
        └── java/com/example/app/
            ├── ApplicationTests.java           # context-loads smoke test
            ├── camel/
            │   ├── CamelRoutesTest.java        # routes with no network
            │   └── GithubRouteMockTest.java    # AdviceWith + MockEndpoint
            ├── web/<feature>/                  # (prescribed) @WebMvcTest slices
            ├── service/<feature>/              # (prescribed) plain JUnit + Mockito
            └── repository/<feature>/           # (prescribed) @DataJpaTest
```

---

## STD-BE-001 — Feature-Package Organization

**Normative Rule**: All presentation-layer code for a feature MUST be co-located in `web/<feature>/` as controller + mapper + a `dto/` sub-package. Do NOT create a parallel `dto/` tree organised by type at the top level.

**When to Apply**: Every new API feature/resource.

**Code Generation Guidance**: When adding a feature `Foo`:

```
web/foo/
  FooController.java
  FooWebMapper.java
  dto/FooResponse.java
  dto/CreateFooRequest.java     (if applicable)
  dto/UpdateFooRequest.java     (if applicable)
  dto/FooSearchRequest.java     (if applicable)
```

Package names are lowercase and singular (`web.foo`, not `web.Foo`, not `web.foos`) even though the URL path is plural — that is Java package convention, and it is deliberate that the two differ.

**Traceability**: `backend/src/main/java/com/example/app/` (`camel/`, `cache/` and the `catalog` slice follow it; other features prescribed)

**Confidence**: Medium (prescriptive — no feature package exists yet)

---

## STD-BE-002 — Controller Base Class and Response Methods

**Normative Rule**: All controllers MUST extend `BaseController`. Controllers MUST use the inherited response helpers instead of building `ResponseEntity` by hand. Handler method names MUST include the entity type: `getassetProfile()`, not `get()`.

**Base class**:

```java
// web/common/BaseController.java
public abstract class BaseController {

    protected <T> ResponseEntity<ApiResponse<T>> handleSuccessResponse(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    protected <T> ResponseEntity<ApiResponse<T>> handleCreatedResponse(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    protected <T> ResponseEntity<ApiResponse<T>> handleErrorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }

    protected <T> ResponseEntity<ApiResponse<T>> handleDeletedResponse() {
        return ResponseEntity.noContent().build();
    }
}
```

**Implementation pattern**:

```java
@RestController
public class assetController extends BaseController {

    private final assetService assetService;
    private final assetWebMapper mapper;

    // Constructor injection only. No @Autowired needed on a single constructor.
    public assetController(assetService assetService, assetWebMapper mapper) {
        this.assetService = assetService;
        this.mapper = mapper;
    }

    @GetMapping("/api/v1/assets/{id}")
    public ResponseEntity<ApiResponse<assetProfileResponse>> getassetProfile(
            @PathVariable long id) {

        return assetService.findProfileById(id)
            .map(asset -> handleSuccessResponse(mapper.toProfileResponse(asset)))
            .orElseGet(() -> handleErrorResponse(
                HttpStatus.NOT_FOUND, "asset not found with id " + id));
    }
}
```

**Anti-Patterns**:
- ❌ `return ResponseEntity.ok(data)` instead of `handleSuccessResponse(data)`
- ❌ Business logic inside a controller method
- ❌ `try`/`catch` in a controller for generic error handling (STD-BE-009 owns that)
- ❌ `@Autowired` on fields — required dependencies become invisible and the class can't be unit-tested without Spring
- ❌ Returning a JPA `@Entity` — it leaks the schema and blows up on lazy associations during serialization

**Traceability**: prescribed; `HelloController` predates this standard and returns a bare `String`

**Confidence**: Medium (prescriptive)

---

## STD-BE-003 — Service Pattern (Business Layer)

**Normative Rule**: Business services MUST be split into an interface (`XxxService`) and an implementation (`XxxServiceImpl`) annotated `@Service`. Services MUST be stateless, MUST own the transaction boundary via `@Transactional`, and MUST return domain records — never entities, never response records.

**Implementation pattern**:

```java
public interface assetService {
    Optional<assetProfileDomain> findProfileById(long assetId);
    Page<assetDomain> search(assetSearchDomain criteria);
    assetDomain create(assetDomain asset);
}
```

```java
@Service
@Transactional(readOnly = true)
public class assetServiceImpl implements assetService {

    private static final Logger log = LoggerFactory.getLogger(assetServiceImpl.class);

    private final assetRepository assetRepository;
    private final assetMapper mapper;

    public assetServiceImpl(assetRepository assetRepository, assetMapper mapper) {
        this.assetRepository = assetRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<assetProfileDomain> findProfileById(long assetId) {
        return assetRepository.findById(assetId)
            .map(mapper::toProfileDomain);
    }

    @Override
    @Transactional                    // overrides readOnly for the write path
    public assetDomain create(assetDomain asset) {
        if (assetRepository.existsByNameIgnoreCase(asset.name())) {
            throw new ApiException(HttpStatus.CONFLICT,
                "asset already exists with name " + asset.name());
        }
        assetEntity saved = assetRepository.save(mapper.toEntity(asset));
        log.info("Created asset {} with id {}", saved.getName(), saved.getId());
        return mapper.toDomain(saved);
    }
}
```

**Why the interface?** Two reasons that matter here: it keeps the controller's compile-time dependency off the implementation (STD-LAY-006), and it lets `@Transactional` work with either proxy strategy. If a feature will provably never have a second implementation and you are comfortable with CGLIB proxies, a single `@Service` class is an accepted variant — but be consistent within a feature.

**Anti-Patterns**:
- ❌ Accepting or returning request/response records in service methods
- ❌ Injecting `EntityManager` or `DataSource` into a service
- ❌ Mutable instance fields (services are singletons — a field is a data race)
- ❌ `@Transactional` on a private or self-invoked method — Spring's proxy never sees the call
- ❌ Returning an entity with uninitialised lazy associations after the transaction closes

**Traceability**: prescribed

**Confidence**: Medium (prescriptive)

---

## STD-BE-004 — Repository Pattern (Data Layer)

**Normative Rule**: Repositories MUST be Spring Data interfaces extending `JpaRepository<T, Long>` and, where filtering is needed, `JpaSpecificationExecutor<T>`. Composable list filters MUST be expressed as `Specification<T>` factory methods. Single-entity lookups by primary key MUST use `findById(id)` — NOT a `Specification`, and NOT `getReferenceById`.

> **Why `findById` not `getReferenceById` for PK lookups**: `getReferenceById` returns an uninitialised proxy and throws `EntityNotFoundException` lazily, at an unpredictable point — often during JSON serialization, far from the call site. `findById` returns `Optional`, which maps cleanly onto the 404 path in STD-API-002.

> **Why no `Specification` for PK lookups**: a `Specification` class adds a file, a factory method, and a `toPredicate` implementation with no composability benefit when the only predicate is equality on the primary key. It produces dead abstraction.

**Repository interface**:

```java
public interface assetRepository
        extends JpaRepository<assetEntity, Long>,
                JpaSpecificationExecutor<assetEntity> {

    boolean existsByNameIgnoreCase(String name);

    Optional<assetEntity> findByNaicCode(String naicCode);
}
```

Do NOT write an implementation class. Spring Data generates it. Do NOT annotate the interface `@Repository` — `JpaRepository` already carries the semantics, and the annotation is redundant on a Spring Data interface.

**Specification pattern**:

```java
// repository/asset/spec/assetSpecifications.java
public final class assetSpecifications {

    private assetSpecifications() {}

    public static Specification<assetEntity> nameContains(String term) {
        if (!StringUtils.hasText(term)) {
            return null;                       // null specs are skipped by Specification.allOf
        }
        return (root, query, cb) -> cb.like(
            cb.lower(root.get("name")), "%" + term.toLowerCase() + "%");
    }

    public static Specification<assetEntity> hasStatus(assetStatus status) {
        return status == null ? null : (root, query, cb) ->
            cb.equal(root.get("status"), status);
    }
}
```

```java
// composed at the service layer
Specification<assetEntity> spec = Specification.allOf(
    assetSpecifications.nameContains(criteria.term()),
    assetSpecifications.hasStatus(criteria.status()));

Page<assetEntity> page = assetRepository.findAll(spec, pageable);
```

> `Specification.allOf(...)` is Spring Data JPA 3.x and skips `null` elements, which is what makes the "return null when the filter is absent" idiom above work. On older versions use `Specification.where(a).and(b)`.

**Exception translation**: let `DataIntegrityViolationException` propagate out of the repository. Translate it at the service boundary, where the business meaning is known:

```java
try {
    return assetRepository.save(entity);
} catch (DataIntegrityViolationException ex) {
    log.error("Constraint violation creating asset {}", entity.getName(), ex);
    throw new ApiException(HttpStatus.CONFLICT, "asset violates a uniqueness constraint");
}
```

**Accepted Variant (VAR-BE-001)**: Complex multi-entity read models MAY bypass `Specification` and use a `@Query` with explicit JPQL and an interface projection, when the query is genuinely not composable.

**Anti-Patterns**:
- ❌ Concatenating user input into a `@Query` string — always use `:namedParameters`
- ❌ `@Transactional` on the repository interface
- ❌ N+1 selects — use `@EntityGraph` or a `join fetch` when loading a collection you will iterate
- ❌ `findAll()` with no `Pageable` on a table that can grow

**Traceability**: prescribed; no repository exists yet

**Confidence**: Medium (prescriptive)

---

## STD-BE-005 — Object Mapping with MapStruct

**Normative Rule**: All cross-layer object transformations MUST use MapStruct with `componentModel = "spring"` so mappers are injectable beans. Each feature MUST have at most two mappers: `service/<feature>/<Feature>Mapper` (entity ↔ domain) and `web/<feature>/<Feature>WebMapper` (domain ↔ request/response). Hand-written property-by-property mapping in a service or controller is PROHIBITED.

**Why MapStruct and not ModelMapper/manual**: MapStruct generates plain Java at compile time. An unmapped target property is a **build warning or error**, not a silent null at runtime — which is exactly the guarantee reflection-based mappers cannot give.

**Dependency** (not yet on the classpath — add it with the first feature that needs it):

```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.6.3</version>
</dependency>
```

MapStruct's annotation processor must run **before** Lombok's, or it will not see Lombok-generated getters. Configure `maven-compiler-plugin` explicitly:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok-mapstruct-binding</artifactId>
                <version>0.2.0</version>
            </path>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>1.6.3</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

`lombok-mapstruct-binding` is not optional when both processors are present. Without it MapStruct generates mappers that ignore every Lombok-generated accessor.

**Three mapping layers**:

```
assetEntity  ←MapStruct→  assetDomain  ←MapStruct→  assetResponse
```

**Implementation**:

```java
// service/asset/assetMapper.java — entity ↔ domain
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface assetMapper {

    @Mapping(target = "id", source = "id")
    assetDomain toDomain(assetEntity entity);

    @Mapping(target = "isDeleted",    ignore = true)
    @Mapping(target = "createdDate",  ignore = true)
    @Mapping(target = "createdBy",    ignore = true)
    @Mapping(target = "modifiedDate", ignore = true)
    @Mapping(target = "modifiedBy",   ignore = true)
    assetEntity toEntity(assetDomain domain);
}
```

```java
// web/asset/assetWebMapper.java — domain ↔ request/response
@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface assetWebMapper {
    assetResponse toResponse(assetDomain domain);
    assetProfileResponse toProfileResponse(assetProfileDomain domain);
    assetDomain toDomain(CreateassetRequest request);
}
```

**`unmappedTargetPolicy = ReportingPolicy.ERROR` is mandatory.** It is the MapStruct equivalent of failing loudly on a forgotten field. The default (`WARN`) means a new field added to a response record silently serializes as `null` forever.

**Anti-Patterns**:
- ❌ Omitting `unmappedTargetPolicy = ReportingPolicy.ERROR`
- ❌ Writing `@Mapping` for properties whose names already match — that is what the library is for
- ❌ Introducing a second mapping library (ModelMapper, Dozer) alongside MapStruct
- ❌ Manual `new assetResponse(entity.getId(), entity.getName(), …)` in a controller
- ❌ Mapping an entity straight to a response, skipping the domain layer

**Traceability**: prescribed; MapStruct is not yet a dependency

**Confidence**: Medium (prescriptive)

---

## STD-BE-006 — Bean Validation for Request Validation

**Normative Rule**: Every request record MUST carry Jakarta Bean Validation constraints, and the controller parameter MUST carry `@Valid`. Validation failures MUST be converted to a 400 `ApiResponse` by the global handler — controllers MUST NOT call a validator or inspect `BindingResult`. Business-rule validation (uniqueness, cross-entity constraints) MUST remain in the service layer, not in a constraint.

`spring-boot-starter-validation` is already on the classpath; no additional dependency is needed.

**Example request record**:

```java
// web/asset/dto/CreateassetRequest.java
public record CreateassetRequest(

    @NotBlank
    @Size(max = 255)
    String name,

    @Email
    @Size(max = 255)
    String emailAddress,

    @Pattern(regexp = "\\d{5}", message = "NAIC code must be 5 digits")
    String naicCode,

    @NotNull
    Long assetTypeId
) {}
```

**Controller wiring**:

```java
@PostMapping("/api/v1/assets")
public ResponseEntity<ApiResponse<assetResponse>> createasset(
        @Valid @RequestBody CreateassetRequest request) { … }
```

`@Valid` on a `@ModelAttribute` (query-bound) parameter works identically but raises `BindException` rather than `MethodArgumentNotValidException`. Both must be handled — see STD-BE-009.

**Nested objects** need `@Valid` on the field too, or the nested constraints are skipped entirely:

```java
public record CreateassetRequest(
    @NotBlank String name,
    @Valid @NotNull AddressRequest address     // without @Valid, AddressRequest's constraints never run
) {}
```

**Anti-Patterns**:
- ❌ Omitting `@Valid` — the annotations become decoration and nothing is validated
- ❌ A custom `ConstraintValidator` that injects a repository to check uniqueness — that is a business rule (STD-LAY-009), and it makes validation transactional and slow
- ❌ Manual `if (request.name() == null) return badRequest()` checks
- ❌ Validating inside the service what the request record already declares

**Traceability**: `backend/pom.xml` (`spring-boot-starter-validation` present, currently unused)

**Confidence**: Medium (dependency present, no usage yet)

---

## STD-BE-007 — SLF4J Structured Logging

**Normative Rule**: All logging MUST use SLF4J with `{}` placeholder templates. String concatenation and `String.format` in log messages are PROHIBITED. A correlation ID MUST be placed in the MDC by a servlet filter and included in the log pattern. Sensitive data (passwords, tokens, PII) MUST NOT be logged.

**Logger declaration** — one of:

```java
private static final Logger log = LoggerFactory.getLogger(assetServiceImpl.class);
```

or, since Lombok is on the classpath:

```java
@Slf4j
public class assetServiceImpl implements assetService { … }
```

Pick one per feature and be consistent.

**Usage**:

```java
log.info("Created asset {} with id {}", saved.getName(), saved.getId());
log.error("Failed to create application '{}' for group {}", app.name(), app.groupCode(), ex);
```

The exception goes in the **last** argument position with no matching `{}` — SLF4J recognises a trailing `Throwable` and prints the stack trace.

**Correlation ID** (prescribed — the filter is not written yet):

```java
// web/common/CorrelationIdFilter.java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = Optional.ofNullable(request.getHeader(HEADER))
            .filter(StringUtils::hasText)
            .orElseGet(() -> UUID.randomUUID().toString());
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);          // MDC is thread-local; a leak poisons the next request
        }
    }
}
```

```properties
logging.pattern.level=%5p [${spring.application.name:app},%X{correlationId:-}]
```

> **Camel caveat**: the MDC does not automatically follow an exchange across an asynchronous Camel hop (`seda:`, `wireTap`, a thread pool). Set `camel.springboot.use-mdc-logging=true` to have Camel populate `camel.exchangeId`, `camel.routeId`, and propagate the MDC across those boundaries.

**Log levels**:

| Level | Use for |
|-------|---------|
| `ERROR` | An operation failed and the user is affected; always include the exception |
| `WARN`  | Recoverable degradation — a retry fired, a fallback was used, a deprecated path was hit |
| `INFO`  | Business-significant events — entity created, integration called, job completed |
| `DEBUG` | Developer diagnostics; off in production |
| `TRACE` | Payload-level detail; never enabled where real data flows |

**Current configuration**:

```properties
logging.level.org.apache.camel=INFO
spring.jpa.show-sql=true          # dev convenience; disable outside dev — it bypasses the logger
```

**Anti-Patterns**:
- ❌ `log.error("Error: " + ex.getMessage())` — no stack trace, no structure
- ❌ `log.info(String.format("id=%d", id))` — defeats the level check and the parameterised format
- ❌ Logging passwords, bearer tokens, connection strings, or full request bodies
- ❌ `System.out.println` anywhere outside a `main` method
- ❌ Catching an exception, logging it, and rethrowing it — the caller logs it again

**Traceability**: `backend/src/main/resources/application.properties`; explicit `.log(...)` steps in `HelloRoute`, `GithubRoute`, `RetryRoute`

**Confidence**: Medium (SLF4J is in use via Camel; the correlation filter is prescribed)

---

## STD-BE-008 — Configuration Management

**Normative Rule**: Configuration MUST be read through Spring's `Environment` — either `@ConfigurationProperties` (preferred for a group of related values) or `@Value` (acceptable for a single value). Hard-coded URLs, ports, credentials, and timeouts in `.java` files are PROHIBITED. Secrets MUST come from environment variables, never from a committed `.properties` file.

**Precedence order** (highest → lowest, Spring Boot's standard order):
1. Command-line arguments
2. Environment variables / `SPRING_APPLICATION_JSON`
3. `application-{profile}.properties`
4. `application.properties`

**Preferred pattern — typed properties**:

```java
@ConfigurationProperties(prefix = "github")
@Validated
public record GithubProperties(
    @NotBlank String baseUrl,
    @Positive int connectTimeoutMs
) {}
```

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class Application { … }
```

`@Validated` on the record means a missing or malformed value fails at **startup**, not on the first request that needs it.

**Current configuration** (`backend/src/main/resources/application.properties`):

```properties
server.port=8080

spring.datasource.url=jdbc:h2:mem:devdb;DB_CLOSE_DELAY=-1
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

cors.allowed-origins=http://localhost:5173

camel.springboot.name=learning-integration
camel.rest.component=platform-http
camel.rest.context-path=/camel
camel.rest.binding-mode=off
camel.rest.inline-routes=false

github.base-url=https://api.github.com
github.connect-timeout-ms=5000
```

> **Camel placeholder syntax differs from Spring's.** Inside a route, reference a property with `{{github.base-url}}` (double braces), not `${github.base-url}`. Camel resolves `{{...}}` through its own `PropertiesComponent`, which is backed by the Spring `Environment` — so the same property file works for both, but the syntax does not interchange.

> ⚠️ **Profile gap**: there is no `application-dev.properties` / `application-prod.properties` split. The H2 console and `show-sql=true` are unconditionally on. Both are dev-only settings and MUST move behind a `dev` profile before anything is deployed.

**Anti-Patterns**:
- ❌ A URL, port, or timeout literal in a `.java` file
- ❌ A password or API key in any committed `.properties` file
- ❌ `System.getenv("FOO")` directly — go through the `Environment` so profiles and defaults still apply
- ❌ `@Value` scattered across a dozen classes for one logical group of settings

**Traceability**: `backend/src/main/resources/application.properties`

**Confidence**: High

---

## STD-BE-009 — Global Exception Handling

**Normative Rule**: Unhandled exceptions MUST be caught by a single `@RestControllerAdvice` and returned as a structured `ApiResponse` with `ApiError` entries. Controllers MUST NOT wrap calls in `try`/`catch` for generic error handling. Domain `ApiException` instances MUST carry the intended HTTP status.

```java
// service/common/ApiException.java
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
```

```java
// web/common/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        log.warn("Handled ApiException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
            .body(ApiResponse.error(ex.getStatus().value(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(BindException ex) {
        List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiError(400, fe.getField() + ": " + fe.getDefaultMessage()))
            .toList();
        return ResponseEntity.badRequest().body(new ApiResponse<>(null, errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
            .body(ApiResponse.error(500, "An unexpected error occurred"));
    }
}
```

Two details worth keeping:
- `MethodArgumentNotValidException` **extends** `BindException`, so one handler method typed to `BindException` covers both — but listing both in `@ExceptionHandler` documents the intent.
- The catch-all returns a generic message. Never echo `ex.getMessage()` from an unknown exception to a client; it leaks class names, SQL fragments, and file paths.

> **Error response envelope format**: see [api-standards.md STD-API-004](api-standards.md) for the normative JSON format, status code reference, and `handleErrorResponse()` usage.
>
> **Camel routes bypass this advice entirely.** An exception inside a route is handled by that route's `onException(...)` clause and never reaches Spring MVC. Keep the JSON shape produced by `onException` consistent with `ApiResponse` — see [integration-guide.md STD-INT-003](integration-guide.md).

**Traceability**: prescribed; `GithubRoute` and `RetryRoute` implement the Camel-side equivalent today

**Confidence**: Medium (prescriptive on the Spring MVC side)

---

## STD-BE-010 — Method Naming and Async Policy

**Normative Rule**: Service and repository methods are **blocking** and MUST NOT carry an `Async` suffix. The `Async` suffix is reserved exclusively for methods that return `CompletableFuture<T>` and are annotated `@Async`.

**Rationale**: this is the deliberate inverse of the .NET convention. In C#, `Async` marks a `Task`-returning method and the suffix is near-universal. In Java on the servlet stack, essentially every method is blocking, so an `Async` suffix on all of them carries zero information — while a suffix on the handful of genuinely asynchronous methods carries a lot.

**Naming patterns**:

| Kind | Pattern | Example |
|------|---------|---------|
| Single lookup returning `Optional` | `findBy…` | `findById`, `findByNaicCode` |
| Single lookup that throws when missing | `get…` | `getRequiredasset` |
| Multiple results | `findAll…` / `search` | `searchassets` |
| Existence check | `existsBy…` | `existsByNameIgnoreCase` |
| Count | `countBy…` | `countByStatus` |
| Create | `create` | `createasset` |
| Full update | `update` | `updateasset` |
| Soft delete | `delete` | `deleteasset` |
| Genuinely async | `…Async` returning `CompletableFuture<T>` | `generateReportAsync` |

Spring Data derived-query methods MUST use the `findBy` / `existsBy` / `countBy` / `deleteBy` prefixes exactly — they are parsed, not decorative.

**When async is genuinely needed**:

```java
@Async
public CompletableFuture<ReportDomain> generateReportAsync(long id) { … }
```

`@Async` requires `@EnableAsync` and a configured `TaskExecutor`; neither exists yet. See [architecture.md STD-ARC-010](architecture.md) for the blocking-stack rule this sits under.

**Confidence**: High (policy decision, no implementation required)

---

## STD-BE-011 — Enum Pattern for `*Type` and `*Status` Fields

**Normative Rule**: Any domain concept stored as a bounded set of named values (`*Type`, `*Status`, `*Category`, `*Group`) MUST be modelled as a Java `enum`. The enum MUST be defined once, in `service/<feature>/enums/`, and used across all three layers.

### Three-Layer Enum Architecture

| Layer | Representation | Convention |
|---|---|---|
| **Entity** (DB) | `@Enumerated(EnumType.STRING)` on the enum field | Persist the **name**, never the ordinal |
| **Domain** (Service) | The typed enum directly | No parsing, no string field |
| **Response** (Web) | The typed enum, or a `{id, code, description}` record | Serialized by Jackson as its name |

### Enum Definition Rules

```java
// service/asset/enums/assetType.java
public enum assetType {
    INSURANCE_AGENCY(1, "Insurance Agency"),
    AUDITOR(2, "Auditor"),
    GOVERNMENT_ENTITY(3, "Government Entity"),
    UNKNOWN(-1, "Unknown");            // MUST be last; null-safe fallback

    private final int code;
    private final String description;

    assetType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code()            { return code; }
    public String description()  { return description; }

    /** Never throws. Returns UNKNOWN for an unrecognised code. */
    public static assetType fromCode(Integer code) {
        if (code == null) return UNKNOWN;
        return Arrays.stream(values())
            .filter(v -> v.code == code)
            .findFirst()
            .orElse(UNKNOWN);
    }
}
```

- Constants are `SCREAMING_SNAKE_CASE` (Java convention), not `PascalCase`.
- Every constant carries an explicit numeric `code` starting at `1`. The code is the stable external identifier; the *name* is the stable database value; the *ordinal* is neither and MUST never be relied on.
- `UNKNOWN` MUST be the last constant and acts as the null-safe sentinel returned by `fromCode`.

### Persistence Rule

```java
@Enumerated(EnumType.STRING)
@Column(name = "asset_type", nullable = false, length = 40)
private assetType assetType;
```

**`EnumType.STRING` is mandatory.** `EnumType.ORDINAL` (the JPA default) stores the declaration index — inserting a new constant anywhere but the end silently reinterprets every existing row. This is the single most damaging default in JPA.

For a persisted numeric code rather than a name, use an `AttributeConverter`:

```java
@Converter(autoApply = true)
public class assetTypeConverter implements AttributeConverter<assetType, Integer> {
    @Override public Integer convertToDatabaseColumn(assetType v) {
        return v == null ? null : v.code();
    }
    @Override public assetType convertToEntityAttribute(Integer c) {
        return assetType.fromCode(c);      // never throws
    }
}
```

### Validation Rule

When a write request accepts a type/status via a numeric id, validate the **id** as a numeric range — Jackson will already reject an unknown enum *name* with a 400:

```java
public record CreateassetRequest(
    @NotNull @Positive Long assetTypeId
) {}
```

Then resolve it in the service via `assetType.fromCode(...)` and reject `UNKNOWN` as a business rule:

```java
assetType type = assetType.fromCode(request.assetTypeId().intValue());
if (type == assetType.UNKNOWN) {
    throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown asset type id " + request.assetTypeId());
}
```

**Anti-Patterns**:
- ❌ `@Enumerated(EnumType.ORDINAL)` or omitting `@Enumerated` on an enum field (ORDINAL is the default)
- ❌ Storing or passing the value as a raw `String` in a domain record or response
- ❌ `assetType.valueOf(input)` without a try/catch — use `fromCode` / a safe lookup
- ❌ Omitting the `UNKNOWN` sentinel
- ❌ Defining the enum under `web/` — the web layer imports it from `service/`, never the reverse (STD-LAY-006)
- ❌ Using `int` instead of the typed enum in a domain or response type
- ❌ Relying on `ordinal()` for anything

**When to Apply**: any property whose name ends in `Type`, `Status`, `Category`, `Group`, or `Code` where the values form a fixed, application-defined set.

**When Not to Apply**: free-text or external reference codes (postal codes, ISO country codes, third-party identifiers) that are not a bounded application-defined set. Those are `String` columns with a length constraint.

**Confidence**: Medium (prescriptive)

---

## AI Implementation Hints

- **Layer flow**: Request/Response (`web`) → Domain (`service`) → Entity (`repository`) — never skip a layer.
- **Mapping**: always a MapStruct `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)`. Never map by hand. Remember `lombok-mapstruct-binding` (STD-BE-005).
- **Validation**: constraints on the request `record`, `@Valid` on the parameter, 400s produced by the `@RestControllerAdvice`. Never inspect `BindingResult` in a controller.
- **Injection**: constructor injection only, fields `private final`, no `@Autowired`.
- **Naming**: no `Async` suffix on blocking methods (STD-BE-010). Spring Data prefixes (`findBy`, `existsBy`, `countBy`) are parsed — spell them exactly.
- **Logging**: SLF4J `{}` placeholders, exception as the trailing argument (STD-BE-007).
- **Enums**: always `@Enumerated(EnumType.STRING)`; always an `UNKNOWN` sentinel; never `ordinal()` (STD-BE-011).
- **Transactions**: `@Transactional(readOnly = true)` on the service class, `@Transactional` overriding it on write methods. Never on a controller, never on a repository interface.
- **Camel**: every route needs `.routeId(...)`; use `.toD(...)` for any URI containing an expression; add `bridgeEndpoint=true` on an outbound producer behind an inbound HTTP consumer.
- **Don't add dependencies speculatively**: MapStruct, springdoc, Actuator, Spring Security, and Flyway are all prescribed but absent. Add one when a story requires it, not because a standard mentions it.
