# Row-Level Security Filtering Pattern

**Repo**: `backend/` | **Stack**: Spring Boot 3.3.2 / Spring Data JPA / Hibernate 6.5 | **Status**: Implementation-validated

How to restrict *which rows* a user may see without inverting the layer dependency — i.e. without injecting a service into a repository.

This file owns the `STD-SEC-*` range. The repository mechanics it builds on are owned by [backend-patterns.md STD-BE-004](backend-patterns.md); the layer boundary rules by [layer-design.md STD-LAY-005 / STD-LAY-006](layer-design.md).

> **Worked example in this repo**: the `invoice` feature implements every standard below. Run
> `./mvnw spring-boot:run` and compare three callers against the same six invoices:
>
> ```
> GET /api/v1/invoices?userId=1&accountId=100   →  totalRecords: 2   (region OH)
> GET /api/v1/invoices?userId=2&accountId=100   →  totalRecords: 2   (region OH|MI AND status PAID)
> GET /api/v1/invoices?userId=3&accountId=100   →  totalRecords: 6   (no policy rows → unrestricted)
> ```
>
> Users 1 and 2 both see two rows, but not the same two. Add `&size=1` to confirm
> `totalRecords` still reports the filtered total — that is the count query being filtered
> by the same specification (STD-SEC-004).
>
> | File | Shows |
> |---|---|
> | `service/security/domain/EntityRestrictions.java` | STD-SEC-002 — the value object that crosses the boundary |
> | `service/security/SecurityPolicyServiceImpl.java` | STD-SEC-005 — fail closed, and the documented "no rows" decision |
> | `repository/security/spec/SecuritySpecifications.java` | STD-SEC-003 — `cb.conjunction()`, never `null` |
> | `repository/invoice/InvoiceRepository.java` | STD-SEC-001 — what a repository looks like with nothing injected |
> | `service/invoice/InvoiceServiceImpl.java` | STD-SEC-004 — resolve once, compose, one `Page<T>` |
> | `repository/invoice/InvoiceRepositorySecurityTest.java` | `@DataJpaTest` with no mocks — the payoff |

---

## Technical Context

| Item | Value | Status |
|------|-------|--------|
| **Coarse authorization** | `@PreAuthorize` at the web layer | ❌ Prescribed (GAP-ARC-006) |
| **Row-level filtering** | `Specification<T>` composed at the service layer | ✅ `invoice` feature |
| **Policy source** | Per-user restriction rows in `user_restriction` | ✅ `invoice` feature |
| **Policy resolution** | Service layer, once per request | ✅ `invoice` feature |
| **Failure mode** | Fail closed — never "no restrictions" on error | ✅ `invoice` feature |
| **Caller identity** | Request parameter (demo shim) | ⚠️ See STD-SEC-006 |
| **Alternative considered** | Hibernate `@SQLRestriction` | ⚠️ Used for soft-delete only — see STD-SEC-003 |
| **Alternative considered** | Database RLS (PostgreSQL / Oracle VPD) | ⚠️ Blocked on [Decision 1](TECHNICAL_DECISIONS_PENDING.md) |

---

## The Shape of the Problem

Row-level security needs two things that sit in different layers:

1. **A policy lookup** — "what is this user allowed to see?" — which is business logic and hits its own tables.
2. **A predicate in the SQL** — which must be in the `WHERE` clause, not applied after the fact, or `LIMIT`/`OFFSET` paginate the wrong set and the total count is wrong.

The tempting shortcut is to inject the policy service into the repository so the predicate can be built where the query is built. That creates an upward dependency and is PROHIBITED by STD-LAY-006:

```text
❌  WRONG — the arrow points back up

    InvoiceService
         │
         ▼
    InvoiceRepositoryImpl ────────► SecurityPolicyService
         │                                 │
         ▼                                 ├─► UserRestrictionRepository
    EntityManager                          └─► RestrictionTemplateRepository
```

```text
✅  RIGHT — resolve above, pass down as data

    InvoiceService
         ├─► securityPolicyService.resolve(userId, accountId, INVOICE)
         │        └─► returns EntityRestrictions (immutable value object)
         │
         └─► invoiceRepository.findAll(spec, pageable)
                  └─► Spring Data
```

The repository stops *asking* what the user may see and is *told*. Same SQL, same rows, one dependency direction.

---

## STD-SEC-001 — Repositories MUST NOT Depend on Services

**Normative Rule**: No type under `repository/` MAY inject, reference, or import a type from `service/`. Security policy, tenancy scope, and any other caller-derived constraint MUST arrive as a method parameter.

**Rationale**:
- **Circular beans.** `InvoiceService → InvoiceRepository → SecurityPolicyService → UserRestrictionRepository` is one edge away from a cycle; Spring resolves some of these with proxies, fails others at startup with `BeanCurrentlyInCreationException`, and which one you get depends on bean creation order.
- **Untestable repositories.** A `@DataJpaTest` must then stand up the service graph. See the Testing section.
- **Invisible side effects.** `repository.findAll(...)` returning a *silently* narrowed result set is indistinguishable from a bug in the caller.
- **Transaction boundaries.** `@Transactional` belongs on the service (STD-DI-004). A repository that calls back into a service crosses that boundary from the wrong side.

**The rule is about direction, not the word "Service".** A repository MAY depend on infrastructure collaborators that sit at or below it:

```java
// ✅ fine — infrastructure, not business logic
public class InvoiceRepositoryImpl {
    private final Clock clock;
    private final FieldEncryptionService fieldEncryptionService;   // column-level crypto
}

// ❌ prohibited — application logic reached downward from the data layer
public class InvoiceRepositoryImpl {
    private final SecurityPolicyService securityPolicyService;
    private final NotificationService notificationService;
}
```

**Anti-Patterns**:
- ❌ Any `service.*` import in a class under `repository/`
- ❌ `userId` / `tenantId` / `accountId` parameters on a repository method whose only purpose is to feed a service call
- ❌ Reading `SecurityContextHolder` inside a repository — same violation, hidden behind a static

**Traceability**: `repository/invoice/InvoiceRepository.java`, `repository/security/UserRestrictionRepository.java`

**Confidence**: High (implemented and tested in the `invoice` feature)

---

## STD-SEC-002 — Resolved Restrictions Are an Immutable Value Object

**Normative Rule**: Resolved policy MUST be carried across the layer boundary as an immutable record with no behaviour and no dependency on any layer. It MUST NOT be a mutable collection, a `Map<String, Object>`, or a service reference.

```java
// service/security/domain/EntityRestrictions.java
public record EntityRestrictions(
        RestrictedEntity entity,
        List<AttributeRestriction> attributes) {

    public EntityRestrictions {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /** Explicit, validated "this user has no restrictions on this entity". */
    public static EntityRestrictions none(RestrictedEntity entity) {
        return new EntityRestrictions(entity, List.of());
    }

    public boolean isEmpty() {
        return attributes.isEmpty();
    }
}

// service/security/domain/AttributeRestriction.java
public record AttributeRestriction(String fieldName, List<String> allowedValues) {

    public AttributeRestriction {
        allowedValues = List.copyOf(allowedValues);
    }
}

// service/security/domain/RestrictedEntity.java
public enum RestrictedEntity { INVOICE, CONTRACT, ASSET }
```

`List.copyOf` in the compact constructor is what makes it safe to pass downward — a repository cannot mutate the caller's policy, and the object can be cached and shared across the two queries in a page request.

**`none()` is not the same as `null`.** `none()` is a resolved decision; `null` is an absent one. STD-SEC-005 depends on being able to tell them apart.

**Anti-Patterns**:
- ❌ Passing `List<AttributeRestriction>` bare — it carries no entity type, so nothing detects INVOICE restrictions applied to a CONTRACT query
- ❌ A mutable `ArrayList` field
- ❌ Putting the `Specification` itself in the value object — that drags Criteria API into the service signature

**Traceability**: `service/security/domain/EntityRestrictions.java`, `AttributeRestriction.java`, `RestrictedEntity.java`

**Confidence**: High (implemented)

---

## STD-SEC-003 — Row Filters MUST Be `Specification<T>`

**Normative Rule**: The restriction-to-predicate translation MUST be a `Specification<T>` factory method under `repository/<feature>/spec/`, composed with the feature's other specifications at the service layer via `Specification.allOf(...)`. A hand-written repository `Impl` with a raw `EntityManager` MUST NOT be used solely to apply security predicates.

This is the JPA repository pattern doing its job: the whole point of `Specification` is composable `WHERE` fragments, and a security filter is exactly that.

```java
// repository/security/spec/SecuritySpecifications.java
public final class SecuritySpecifications {

    private SecuritySpecifications() {}

    public static <T> Specification<T> restrictedBy(EntityRestrictions restrictions) {
        Objects.requireNonNull(restrictions, "restrictions must be resolved, never null");

        return (root, query, cb) -> {
            if (restrictions.isEmpty()) {
                // An always-true predicate, NOT null. See the note below.
                return cb.conjunction();
            }
            return cb.and(restrictions.attributes().stream()
                    .map(attr -> root.get(attr.fieldName()).in(attr.allowedValues()))
                    .toArray(Predicate[]::new));
        };
    }
}
```

```java
// composed at the service layer, alongside ordinary user filters
Specification<InvoiceEntity> spec = Specification.allOf(
        InvoiceSpecifications.numberContains(criteria.term()),   // may be null — optional filter
        InvoiceSpecifications.issuedBetween(criteria.dateRange()),
        SecuritySpecifications.restrictedBy(restrictions));      // never null — security

Page<InvoiceEntity> page = invoiceRepository.findAll(spec, pageable);
```

> **This deliberately breaks the `null`-spec idiom from STD-BE-004.** That idiom — "return `null` when the filter is absent, `Specification.allOf` skips it" — is correct for *optional user filters*, where a skipped filter means a wider result set and that is what the user asked for. For a *security* filter, a `null` returned by accident is a silently disabled access control. Returning a non-null `cb.conjunction()` means a bug produces a broken query, not a data leak. `Objects.requireNonNull` enforces the same thing on the way in.

**Why not `@SQLRestriction`**: it is a static, entity-wide string evaluated per session, so it cannot express a per-user value list. It is the right tool for soft-delete (STD-DB-005) and the wrong one here.

**Why not database RLS**: PostgreSQL RLS / Oracle VPD is the strongest option and moves the predicate below the ORM where nothing can forget it — but it depends on a production datasource decision and moves policy out of the reviewed codebase. Revisit after [Decision 1](TECHNICAL_DECISIONS_PENDING.md).

**Anti-Patterns**:
- ❌ Filtering in Java after `getResultList()` — breaks pagination and the total count, and reads rows the user may not see
- ❌ A `Specification` that calls a service inside `toPredicate` (STD-SEC-001 through the back door)
- ❌ Interpolating `allowedValues` into a `@Query` string — use `.in(...)` or a named parameter
- ❌ Returning `null` from a security specification factory

**Traceability**: `repository/security/spec/SecuritySpecifications.java`, `repository/invoice/spec/InvoiceSpecifications.java`

**Confidence**: High (implemented; `InvoiceRepositorySecurityTest` covers the empty and null cases)

---

## STD-SEC-004 — Resolve Once Per Request; Page, Don't Count Separately

**Normative Rule**: Policy MUST be resolved once per request and reused for every query in that request. Paginated reads MUST use `Pageable` and return `Page<T>`; a separate hand-written count method taking the same security parameters is PROHIBITED.

```java
@Service
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final SecurityPolicyService securityPolicyService;
    private final InvoiceMapper mapper;

    public InvoiceServiceImpl(
            InvoiceRepository invoiceRepository,
            SecurityPolicyService securityPolicyService,
            InvoiceMapper mapper) {
        this.invoiceRepository = invoiceRepository;
        this.securityPolicyService = securityPolicyService;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Invoice> search(InvoiceCriteria criteria, Long userId, Long accountId, Pageable pageable) {

        // Resolved once. Used by the rows query and the count query Spring Data derives from it.
        EntityRestrictions restrictions =
                securityPolicyService.resolve(userId, accountId, RestrictedEntity.INVOICE);

        Specification<InvoiceEntity> spec = Specification.allOf(
                InvoiceSpecifications.numberContains(criteria.term()),
                SecuritySpecifications.restrictedBy(restrictions));

        log.debug("Invoice search for user {} with {} restriction(s)", userId, restrictions.attributes().size());

        return invoiceRepository.findAll(spec, pageable).map(mapper::toDomain);
    }
}
```

`JpaSpecificationExecutor.findAll(spec, pageable)` issues the rows query **and** the count query from the same `Specification`. A hand-rolled `countXxx(...)` method is not just duplication — it is a second place for the security predicate to drift out of sync with the first, and the drift is invisible (the page looks right, the total is wrong, or vice versa).

**The resolution is cacheable.** Policy changes rarely and is read on every request:

```java
@Cacheable(value = "entityRestrictions", key = "#userId + ':' + #accountId + ':' + #entity")
public EntityRestrictions resolve(Long userId, Long accountId, RestrictedEntity entity) { … }
```

> `spring-boot-starter-cache` is not on the classpath (see [caching-pattern.md](caching-pattern.md) for why). Use `RedisCache.getOrLoad` with a `CacheKeys` entry instead, and remember `@Cacheable`'s self-invocation trap.

**Anti-Patterns**:
- ❌ Resolving policy inside both a `findXxx` and a `countXxx` — four extra round-trips per page load, and two chances to diverge
- ❌ `page` / `pageSize` / `sortField` / `sortDirection` as four loose parameters — that is `Pageable` (STD-API-003)
- ❌ Resolving policy per row, or inside a loop
- ❌ `@Async` on a repository method. The stack is blocking-by-default (STD-ARC-010); async belongs at the service layer if anywhere, and `@Async` wrapped around a `CompletableFuture.supplyAsync(...)` body hops threads twice — the second onto `ForkJoinPool.commonPool()`, which is sized for CPU work, not blocking JDBC

**Traceability**: `service/invoice/InvoiceServiceImpl.java`

**Confidence**: High (implemented; `InvoiceServiceImplTest` asserts a single `resolve` per search)

---

## STD-SEC-005 — Fail Closed

**Normative Rule**: A policy resolution that cannot complete MUST throw. It MUST NOT return an empty restriction set. "No restrictions resolved" and "no restrictions apply" MUST be distinguishable in code.

This is the highest-severity rule in this file, because the failure is silent and it is a data leak rather than an outage.

```java
// ❌ FAIL OPEN — a transient DB error grants full visibility
public List<AttributeRestriction> resolve(Long userId, Long accountId, RestrictedEntity entity) {
    try {
        …
    } catch (Exception e) {
        log.error("Error resolving restrictions for user {}", userId, e);
        return new ArrayList<>();      // empty → no predicate → every row
    }
}
```

The chain is: empty list → no predicate added → `WHERE` has no restriction → the user sees the whole table. Nothing logs at `ERROR` on the request path, the endpoint returns `200`, and the page renders normally with more data than it should.

```java
// ✅ FAIL CLOSED
@Transactional(readOnly = true)
public EntityRestrictions resolve(Long userId, Long accountId, RestrictedEntity entity) {

    if (userId == null || accountId == null) {
        throw new ApiException(HttpStatus.UNAUTHORIZED,
                "Cannot resolve %s restrictions without a user and account".formatted(entity));
    }

    try {
        List<AttributeRestriction> attributes = loadRestrictions(userId, accountId, entity);
        return new EntityRestrictions(entity, attributes);
    } catch (DataAccessException e) {
        log.error("Policy store unavailable for user {} account {}", userId, accountId, e);
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Access policy is temporarily unavailable");
    }
}
```

**Decide explicitly what "user has no policy row" means.** Returning `EntityRestrictions.none()` for a user with no configured policy means *unrestricted access by default* — which is a reasonable design only if it is a deliberate one. Record the choice in the method's Javadoc; do not leave it as a fallthrough.

**Anti-Patterns**:
- ❌ `catch (Exception e) { return emptyList(); }` on any path that feeds a security predicate
- ❌ `catch (Exception e) { return emptyList(); }` in a repository query method generally — it turns a broken query into a `200` with an empty page, and hides the stack trace from the caller (STD-LAY-007: repositories never swallow)
- ❌ Silently skipping the filter when `userId` is null
- ❌ Catching bare `Exception` where `DataAccessException` is meant — it also swallows the `ApiException` thrown two lines up

**Traceability**: `service/security/SecurityPolicyServiceImpl.java`, `AccessPolicyUnavailableException.java`

**Confidence**: High (implemented; `SecurityPolicyServiceImplTest` covers the store-unavailable path)

---

## STD-SEC-006 — Where Each Authorization Check Lives

**Normative Rule**: Authorization MUST be applied at the layer matching its granularity. Endpoint-level checks MUST be `@PreAuthorize` at the web layer. Row-level checks MUST be a `Specification` composed at the service layer. Neither MAY live in the repository.

| Question | Layer | Mechanism |
|---|---|---|
| Who is the caller? (authentication) | Filter chain | `SecurityFilterChain` → `401` when it fails |
| May this principal call this endpoint? | `web/` | `@PreAuthorize("hasRole('…')")` → `403` |
| May this principal act on *this* record? | `service/` | Ownership check → `ApiException(FORBIDDEN)` |
| Which records may this principal list? | `service/` → `repository/` | `Specification` in the `WHERE` clause |

**Identity lookup is authentication, not authorization.** Loading a user and verifying credentials answers "are you who you claim"; a missing user is a `401`, not a `403`, because identity was never established. Permission checks only start once it has been.

> Return an identical `401` for "user not found" and "wrong credentials". Distinguishing them turns the login endpoint into a user-enumeration oracle. See [api-standards.md STD-API-006](api-standards.md) for the auth response contract.

**On `anyRequest().permitAll()` plus method security**: a filter chain that permits everything and relies entirely on `@PreAuthorize` is a valid posture, but it is fail-open by construction — any new endpoint that forgets the annotation is public, and nothing fails at build or startup. If you adopt it, add an ArchUnit test asserting every `@RestController` public method carries an authorization annotation.

**Anti-Patterns**:
- ❌ An ownership check in a controller: `if (!repo.findById(id).get().getOwnerId().equals(userId))`
- ❌ Row filtering applied in the web layer after the service returns — the count is already wrong
- ❌ `403` for an unauthenticated caller, `401` for an authenticated-but-forbidden one (HTTP names them backwards: `401 Unauthorized` means *unauthenticated*)

**Confidence**: Low (prescriptive — Spring Security is not on the classpath, GAP-ARC-006)

---

## Accepted Variant — VAR-SEC-001 — Criteria API for Multi-Join DTO Projections

A hand-written `XxxRepositoryCustom` / `XxxRepositoryImpl` pair using `EntityManager` and `CriteriaBuilder` is ACCEPTED when the read model genuinely cannot be expressed as a `Specification` — typically a `cb.construct(...)` DTO projection across several joins, where `Specification` only hands you the `Root`. This is the same carve-out as VAR-BE-001.

**The variant relaxes the mechanism, not STD-SEC-001.** The custom implementation still receives resolved restrictions as a parameter:

```java
public interface InvoiceRepositoryCustom {
    Page<InvoiceSummary> searchSummaries(InvoiceQuery query, Pageable pageable);
}

/** Everything the criteria query needs, including pre-resolved policy. */
@Builder
public record InvoiceQuery(
        List<Long> accountIds,
        InvoiceFilters filters,
        EntityRestrictions restrictions) {

    public InvoiceQuery {
        Objects.requireNonNull(restrictions, "restrictions must be resolved by the service layer");
    }
}
```

```java
public class InvoiceRepositoryImpl implements InvoiceRepositoryCustom {

    @PersistenceContext private EntityManager entityManager;
    // No service field. The Objects.requireNonNull above is the contract.

    @Override
    public Page<InvoiceSummary> searchSummaries(InvoiceQuery query, Pageable pageable) {
        …
        if (!query.restrictions().isEmpty()) {
            predicates.add(SecurityPredicates.build(invoice, cb, query.restrictions()));
        }
        …
    }
}
```

Collapsing a long parameter list into one `record` is required, not cosmetic: a signature like `find(accountIds, filters, page, pageSize, sortField, sortDirection, userId, accountId)` makes the two security parameters look like ordinary filters, which is how they end up feeding a service call inside the repository in the first place.

---

## Migration Recipe — Removing an Injected Service from a Repository

Behaviour-preserving, in this order. Each step compiles.

1. **Add the value object** — `EntityRestrictions`, `AttributeRestriction`, `RestrictedEntity` (STD-SEC-002).
2. **Add `resolve(...)` to the policy service**, returning the value object. Keep the existing method so current callers still compile. Reproduce the repository's existing null-guard here — if the repository did `if (userId != null && accountId != null)`, `resolve` returns `EntityRestrictions.none()` for those inputs. Behaviour is identical at this point; STD-SEC-005 comes later, as its own commit.
3. **Add the `Specification` factory** (STD-SEC-003), or the `record` parameter object if VAR-SEC-001 applies.
4. **Change the repository signature** to take the restrictions, delete the service field, replace the in-repository resolution with the passed-in value.
5. **Move resolution up to the service**, once per request, and switch the paired `find`/`count` calls to a single `Page<T>` (STD-SEC-004).
6. **Separate commit: fail closed** (STD-SEC-005). This one deliberately changes behaviour — errors that previously produced a wider result set now produce a `503`. It needs its own review and its own test.

Steps 1–5 produce identical SQL and identical rows. Two side effects, both benign:

- Policy resolves once instead of once per query — fewer round-trips, same inputs, same output.
- Resolution moves from a pool thread onto the request thread, so it runs inside the caller's transaction and security context.

---

## Testing

The payoff for STD-SEC-001 is that both sides become testable alone.

```java
// Repository: no service graph, no mocks — @DataJpaTest only works because
// the repository has no service dependency.
@DataJpaTest
class InvoiceRepositorySecurityTest {

    @Autowired InvoiceRepository repository;

    @Test
    void findAll_should_return_only_rows_matching_restrictions() {
        EntityRestrictions restrictions = new EntityRestrictions(
                RestrictedEntity.INVOICE,
                List.of(new AttributeRestriction("region", List.of("OH"))));

        Page<InvoiceEntity> page = repository.findAll(
                SecuritySpecifications.restrictedBy(restrictions), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(InvoiceEntity::getRegion).containsOnly("OH");
        assertThat(page.getTotalElements()).isEqualTo(2);   // count query is filtered too
    }

    @Test
    void findAll_should_return_all_rows_when_restrictions_are_empty() {
        Page<InvoiceEntity> page = repository.findAll(
                SecuritySpecifications.restrictedBy(EntityRestrictions.none(RestrictedEntity.INVOICE)),
                PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(5);
    }
}
```

```java
// Service: policy store failure must not widen the result set.
@Test
void search_should_fail_closed_when_policy_store_is_unavailable() {
    when(userRestrictionRepository.findByUserId(7L))
            .thenThrow(new QueryTimeoutException("policy store timeout"));

    assertThatThrownBy(() -> service.search(criteria, 7L, 100L, PageRequest.of(0, 20)))
            .isInstanceOf(ApiException.class)
            .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

    verifyNoInteractions(invoiceRepository);
}
```

**Every restriction rule needs a negative test.** A security filter that is accidentally disabled still passes every positive assertion — the rows the user *should* see are all present. Only an assertion on the rows they should *not* see catches it. Naming follows STD-TST-001 (`methodName_should_expectedBehavior`).

An ArchUnit test makes STD-SEC-001 mechanical rather than a review convention:

```java
@Test
void repositories_should_not_depend_on_services() {
    noClasses().that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAPackage("..service..")
            .check(new ClassFileImporter().importPackages("com.example.app"));
}
```

---

## AI Implementation Hints

- **Never inject a service into a repository.** Resolve the policy one layer up and pass it down as an immutable record. The rule is about dependency direction, not the word "Service".
- **A security filter is a `Specification`.** That is what the JPA repository pattern is for — do not reach for a hand-written `EntityManager` impl just to add a predicate.
- **Security specs return `cb.conjunction()`, never `null`.** The `null`-is-skipped idiom from STD-BE-004 is for optional user filters only; here it is a silent access-control bypass.
- **Use `Page<T>`, not a `find` + `count` pair.** One `Specification` feeds both queries, so they cannot drift.
- **Resolve once per request**, then cache. Not once per query, never per row.
- **Fail closed.** `catch (Exception e) { return emptyList(); }` on a policy path is a data leak, not error handling.
- **Filter in SQL, never in Java after the fetch** — post-filtering breaks `LIMIT`/`OFFSET` and the total count.
- **`401` is unauthenticated, `403` is forbidden**, and HTTP's own names for them are misleading. Missing user is `401`.
- **Write the negative test.** A disabled filter passes every positive assertion.
