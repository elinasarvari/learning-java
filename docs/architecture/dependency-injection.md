# Dependency Injection

**Repo**: `backend/` | **Container**: Spring Framework 6 (via Spring Boot 3.3.2) | **Status**: Partly implementation-validated

---

## STD-DI-001 — Component Scan First, `@Configuration` for the Rest

**Normative Rule**: Application beans (controllers, services, repositories, Camel routes, mappers) MUST be discovered by component scan using the correct stereotype annotation. An explicit `@Bean` method MUST be used only for beans this project does not own — third-party clients, `RestClient` instances, converters, filters that need constructor arguments — and those methods MUST be grouped in a per-feature `@Configuration` class named `<Feature>Config` under `com.example.app.config`.

**Rationale**: `@SpringBootApplication` on `com.example.app.Application` already scans the whole `com.example.app` tree. Re-declaring an owned bean as an explicit `@Bean` duplicates the wiring and creates two ways for a class to become a bean — which is how duplicate-bean and shadowed-bean bugs start.

**Stereotype by layer**:

| Layer | Annotation | Note |
|-------|-----------|------|
| Controller | `@RestController` | implies `@Component` |
| Service impl | `@Service` | on `XxxServiceImpl`, never on the interface |
| Spring Data repository | *(none)* | `JpaRepository` interfaces are proxied automatically — do NOT add `@Repository` |
| Custom repository impl | `@Repository` | only for a hand-written implementation |
| MapStruct mapper | *(none)* | `@Mapper(componentModel = "spring")` generates the `@Component` |
| Camel `RouteBuilder` | `@Component` | how `camel-spring-boot-starter` discovers routes |
| Bean called from a Camel route | `@Component("beanName")` | the explicit name is what `.bean("beanName", "method")` resolves |
| Servlet filter | `@Component` | or a `FilterRegistrationBean` when ordering matters |
| Typed properties | `@ConfigurationProperties` | discovered via `@ConfigurationPropertiesScan` |

**Live example** — the Camel layer already follows this:

```java
// backend/src/main/java/com/example/app/camel/HelloRoute.java
@Component
public class HelloRoute extends RouteBuilder { … }

// backend/src/main/java/com/example/app/camel/FlakyService.java
@Component("flakyService")            // the name matters — RetryRoute calls .bean("flakyService", "maybeFail")
public class FlakyService { … }
```

If `FlakyService` lost its explicit bean name, Spring would register it as `flakyService` by convention anyway — but relying on that convention is fragile the moment the class is renamed. Name it explicitly whenever a Camel route resolves it by string.

**Configuration class pattern** (prescribed):

```java
// config/GithubConfig.java
@Configuration
public class GithubConfig {

    @Bean
    RestClient githubRestClient(GithubProperties properties) {
        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader("User-Agent", "camel-learning-demo")
            .defaultHeader("Accept", "application/vnd.github+json")
            .build();
    }
}
```

**Code Generation Guidance**: when scaffolding a feature `Foo`:
1. Create `service/foo/FooService.java` (interface — no annotation)
2. Create `service/foo/FooServiceImpl.java` annotated `@Service`
3. Create `repository/foo/FooRepository.java` extending `JpaRepository` (no annotation)
4. Create `web/foo/FooController.java` annotated `@RestController`
5. Only create `config/FooConfig.java` if the feature needs a third-party bean

**Anti-Patterns**:
- ❌ `@Repository` on a Spring Data interface — redundant, and it implies a hand-written implementation exists
- ❌ `@Service` on the interface rather than the implementation
- ❌ A `@Bean` method returning a class that is already `@Service`-annotated
- ❌ `@ComponentScan` with an explicit `basePackages` that re-lists `com.example.app` — `@SpringBootApplication` already does it
- ❌ A single god `AppConfig` holding every `@Bean` in the application

**Traceability**:
- `backend/src/main/java/com/example/app/Application.java`
- `backend/src/main/java/com/example/app/camel/` (all four classes)

**Confidence**: High (the Camel layer demonstrates the pattern)

---

## STD-DI-002 — Constructor Injection Only

**Normative Rule**: All dependencies MUST be injected through the constructor and stored in `private final` fields. Field injection (`@Autowired` on a field) and setter injection are PROHIBITED. A class with exactly one constructor MUST NOT annotate it `@Autowired` — Spring infers it.

```java
@Service
@Transactional(readOnly = true)
public class assetServiceImpl implements assetService {

    private final assetRepository assetRepository;
    private final assetMapper mapper;

    public assetServiceImpl(assetRepository assetRepository, assetMapper mapper) {
        this.assetRepository = assetRepository;
        this.mapper = mapper;
    }
}
```

**Why constructor injection**:
- The dependency list is visible in the signature — a class with nine constructor parameters is visibly doing too much, whereas nine `@Autowired` fields look tidy
- Fields can be `final`, so the object is immutable and safe to publish across threads (services are singletons)
- The class can be instantiated in a plain JUnit test with `new assetServiceImpl(mockRepo, mapper)` — no Spring context, no reflection, no `@InjectMocks`
- A missing bean fails at context startup, not at first use

**Lombok alternative** — acceptable, since Lombok is already on the classpath:

```java
@Service
@RequiredArgsConstructor
public class assetServiceImpl implements assetService {
    private final assetRepository assetRepository;
    private final assetMapper mapper;
}
```

`@RequiredArgsConstructor` generates the constructor from the `final` fields. Choose one style per feature; do not mix a hand-written constructor and `@RequiredArgsConstructor` in the same class.

**Circular dependencies**: constructor injection makes a cycle a startup failure rather than a lazily-discovered mystery. That is the correct behaviour. Do NOT resolve it with `@Lazy` or `spring.main.allow-circular-references=true` — a cycle means the responsibilities are split wrong. Extract the shared logic into a third bean.

**Anti-Patterns**:
- ❌ `@Autowired private assetRepository repo;`
- ❌ `@Autowired` on a sole constructor (redundant since Spring 4.3)
- ❌ Non-final dependency fields
- ❌ `@Lazy` or `allow-circular-references=true` to paper over a cycle
- ❌ `ApplicationContext.getBean(...)` — service location instead of injection

**Confidence**: High

---

## STD-DI-003 — Singleton Scope and Statelessness

**Normative Rule**: Every application bean is a **singleton** — Spring's default — and MUST therefore be stateless. Mutable instance fields are PROHIBITED on controllers, services, repositories, mappers, and `RouteBuilder`s. `@Scope("prototype")` and `@Scope("request")` MUST NOT be used for application beans.

**Rationale**: this is the substantive difference from a per-request DI container. In Spring, one instance of `assetServiceImpl` serves every concurrent request. A non-final instance field on a service is a data race, not a caching optimisation.

**Where per-request state actually lives**:

| Concern | Correct home |
|---------|-------------|
| The current request's data | Method parameters |
| The current transaction / persistence context | The `EntityManager` proxy, managed by `@Transactional` |
| The correlation ID | SLF4J `MDC` (thread-local, cleared in a `finally`) — STD-BE-007 |
| Per-exchange integration state | Camel `Exchange` headers and properties |
| The authenticated principal | `SecurityContextHolder` (once Spring Security lands) |

**Accepted Variant (VAR-DI-001)**: `FlakyService` holds an `AtomicInteger attempts` field — genuine mutable singleton state:

```java
// backend/src/main/java/com/example/app/camel/FlakyService.java
@Component("flakyService")
public class FlakyService {
    private final AtomicInteger attempts = new AtomicInteger();
    …
}
```

This is deliberate and safe: it is a test double whose entire purpose is to fail deterministically on a call count, and `AtomicInteger` makes the counter thread-safe. Note that `CamelRoutesTest.flakyRouteSucceedsAfterRedeliveries` is written to tolerate any starting value of that counter precisely *because* the state is shared across tests. Do not copy this pattern into a business service.

**Anti-Patterns**:
- ❌ A mutable `List`, `Map`, or counter field on a service (unless it is a thread-safe cache, in which case use a real cache)
- ❌ `@Scope("request")` on a service to "make it safe" — it makes injection into a singleton require a proxy and hides the design problem
- ❌ A `ThreadLocal` field that is never cleared — with pooled request threads, that leaks state into the next unrelated request
- ❌ Storing the current user, tenant, or request id in a service field

**Traceability**: `backend/src/main/java/com/example/app/camel/FlakyService.java`, `backend/src/test/java/com/example/app/camel/CamelRoutesTest.java`

**Confidence**: High

---

## STD-DI-004 — Transaction Boundaries

**Normative Rule**: `@Transactional` MUST be declared on the **service implementation**, never on a controller, a repository interface, or a Camel route. Service classes MUST declare `@Transactional(readOnly = true)` at class level, with write methods overriding it with a plain `@Transactional`.

```java
@Service
@Transactional(readOnly = true)          // default for every method
public class assetServiceImpl implements assetService {

    @Override
    public Optional<assetDomain> findById(long id) { … }     // inherits readOnly

    @Override
    @Transactional                                              // overrides: read-write
    public assetDomain create(assetDomain asset) { … }
}
```

**Why the service and not the repository**: a business operation that touches two repositories must succeed or fail as a unit. If each repository call opened its own transaction, a failure in the second would leave the first committed.

**Why `readOnly = true` by default**: it lets Hibernate skip dirty-checking and flush on read paths, and it makes an accidental write on a read path fail loudly instead of silently persisting.

**The self-invocation trap**: Spring implements `@Transactional` with a proxy. A call from one method of a bean to another method of the *same* bean bypasses the proxy entirely, so the annotation on the inner method does nothing:

```java
public void importAll(List<assetDomain> assets) {
    assets.forEach(this::createOne);   // ❌ createOne's @Transactional is IGNORED
}

@Transactional
public void createOne(assetDomain asset) { … }
```

Fix it by moving the inner method to a separate bean, or by putting the annotation on the outer entry point.

**Anti-Patterns**:
- ❌ `@Transactional` on a controller method — it holds a database connection for the entire HTTP round trip, including serialization
- ❌ `@Transactional` on a Spring Data repository interface
- ❌ `@Transactional` on a `private`, `protected`, or `final` method — the proxy cannot intercept it (CGLIB cannot override `final`; a private method is never on the proxy's surface)
- ❌ Self-invocation of a `@Transactional` method
- ❌ Catching an exception inside a `@Transactional` method and returning normally — the transaction still commits, so a partial write is persisted
- ❌ Calling an external HTTP service inside a transaction — a slow upstream now holds a database connection

**Confidence**: Medium (prescriptive — no `@Transactional` usage exists yet)

---

## STD-DI-005 — Camel Bean Resolution

**Normative Rule**: A bean invoked from a Camel route via `.bean("name", "method")` MUST be a `@Component` with an **explicit** bean name matching the route's string, and the target method MUST be `public` and unambiguous by name. Camel resolves both the bean and the method by string at route-build time; a typo in either is a startup failure, not a compile error.

**Live example**:

```java
// RetryRoute.java
from("direct:flaky")
    .routeId("flaky")
    .bean("flakyService", "maybeFail")
```

```java
// FlakyService.java
@Component("flakyService")
public class FlakyService {
    public String maybeFail() { … }
    public String alwaysFail() { … }
}
```

**Prefer the typed form where practical.** `.bean(FlakyService.class, "maybeFail")` still resolves the method by string but at least binds the type at compile time, so a class rename is caught by the compiler.

**Overload hazard**: if `FlakyService` gained a second `maybeFail(String)`, Camel would have to guess which overload to call and would fail with an ambiguity error at startup. Do not overload a method that a route resolves by name — give the second variant a distinct name.

**Calling a service from a route**: a route that needs persistence MUST go through an `XxxService` bean, never a repository (STD-LAY-011). Wire it the same way:

```java
from("direct:importasset")
    .routeId("import-asset")
    .bean(assetService.class, "create");
```

**Anti-Patterns**:
- ❌ Relying on Spring's default bean-name convention when a route hard-codes the string
- ❌ Overloading a method that a route resolves by name
- ❌ `.bean(...)` targeting a repository directly
- ❌ Constructing a service with `new` inside `configure()`

**Traceability**: `backend/src/main/java/com/example/app/camel/RetryRoute.java`, `backend/src/main/java/com/example/app/camel/FlakyService.java`

**Confidence**: High

---

## AI Implementation Hints

- **Default**: component scan + the right stereotype. Reach for `@Bean` only for a type this project does not own.
- **Never** annotate a Spring Data `JpaRepository` interface with `@Repository`.
- **Constructor injection only**, `private final` fields, no `@Autowired` on a sole constructor. `@RequiredArgsConstructor` is an acceptable shorthand.
- **Everything is a singleton** — no mutable instance fields. `FlakyService` is a deliberate exception (VAR-DI-001), not a template.
- **`@Transactional` lives on the service implementation**: `readOnly = true` at class level, plain `@Transactional` on writes.
- **Pitfall**: a self-invoked `@Transactional` method does nothing. Same for `private` and `final` methods.
- **Pitfall**: a Camel `.bean("name", "method")` string is resolved at runtime — rename a bean and the route breaks at startup with no compiler warning.
- **Pitfall**: do not fix a circular dependency with `@Lazy`; split the responsibility instead.
