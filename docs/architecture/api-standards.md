# API Standards Guide

This guide documents the RESTful API conventions for the learning-java backend. Where the codebase already demonstrates a pattern it is cited; where it does not, the rule is prescriptive and marked as such.

---

## STD-API-001 — Route Pattern

**Normative Rule**: All Spring MVC routes MUST follow the pattern `/api/v1/[resource]`. The version (`v1`) MUST be included in every route path, declared on the handler method, not as a class-level `@RequestMapping`. Routes MUST use plural nouns. Current API version is `v1`.

**Examples**:

```
GET    /api/v1/assets                   List assets
GET    /api/v1/assets/{id}              Get a specific asset
GET    /api/v1/contacts/search             Search contacts
GET    /api/v1/contacts/{id}               Get a specific contact
POST   /api/v1/contacts                    Create a contact
PUT    /api/v1/contacts/{id}               Update a contact
DELETE /api/v1/contacts/{id}               Delete a contact
```

**Rules**:
- ✅ Plural nouns: `/api/v1/assets`, `/api/v1/contacts`, `/api/v1/users`
- ✅ HTTP verbs semantically: GET (read), POST (create), PUT (full update), DELETE (remove)
- ✅ Route declared per-method: `@GetMapping("/api/v1/assets/{id}")`
- ✅ Nested resources for relationships: `/api/v1/assets/{id}/addresses`
- ❌ No verbs in URLs: `/api/v1/getassets`, `/api/v1/createContact`
- ❌ No deep nesting beyond 2 levels
- ✅ Handler method name includes the entity type: `getassetProfile()`, not `get()`
- **Versioning**: URL-based versioning is the only supported strategy. When a breaking change is required, introduce a new prefix (`/api/v2/`). Header-based and media-type versioning are NOT used.

**Accepted Variant (VAR-API-001)**: Custom action routes are permitted using `/api/v1/[resource]/[action]` (e.g. `/api/v1/assets/bulk-addresses`) for non-CRUD operations.

**Accepted Variant (VAR-API-002)**: `HelloController` maps `/api/hello` — no version segment, singular noun, returns a bare `String`. It is starter scaffolding. Do not copy it; migrate it when next touched.

---

## STD-API-007 — Camel Route Namespace

**Normative Rule**: Apache Camel routes are served under the `/camel/*` prefix and are **exempt** from STD-API-001. The prefix is set once, globally, in `application.properties` and MUST NOT be overridden per route.

```properties
camel.rest.component=platform-http
camel.rest.context-path=/camel
camel.rest.binding-mode=off
camel.rest.inline-routes=false
```

**Why the namespace split**: Camel's `platform-http` component reuses the servlet container Spring Boot already started, so both stacks share port 8080. Keeping Camel under `/camel/*` guarantees it can never collide with a `/api/v1/*` mapping and makes it obvious from a URL alone which stack handles a request.

**Why `binding-mode=off`**: `json` binding silently re-encodes a body that is already a JSON string, producing a double-escaped payload. Routes marshal explicitly instead — `.marshal().json()` or a `setBody(simple("{...}"))` plus `setHeader(Exchange.CONTENT_TYPE, constant("application/json"))`.

**Why `inline-routes=false`**: Camel 4 defaults this to `true`, which fuses each `rest(...)` route into the `direct:` route it calls, leaving no independently addressable `direct:` endpoint. Tests sending to `direct:hello` then fail with `DirectConsumerNotAvailable`.

**Current Camel endpoints**:

```
GET /camel/hello              → direct:hello        constant greeting
GET /camel/hello/{name}       → direct:helloName    path param arrives as a HEADER
GET /camel/gh/user/{user}     → direct:ghUser       correct outbound HTTP call
GET /camel/gh/broken/{user}   → direct:ghBroken     reproduces the bridgeEndpoint trap (502)
GET /camel/gh/fail            → direct:ghFail       real upstream 404 → mapped to 502
GET /camel/flaky              → direct:flaky        succeeds on attempt 3 after redeliveries
GET /camel/boom               → direct:boom         4 attempts, then a 503 fallback
```

**Traceability**: `backend/src/main/java/com/example/app/camel/`, `backend/src/main/resources/application.properties`

**Confidence**: High

---

## STD-API-002 — HTTP Method Conventions

**Normative Rule**: HTTP methods MUST map semantically to their operation: GET for reads (200), POST for creates (201 via `handleCreatedResponse`), PUT for full updates (200), DELETE for removal (204 via `handleDeletedResponse`). All endpoints MUST declare their expected response codes for OpenAPI once springdoc is adopted (see the Swagger callout below).

### GET — Retrieve a Resource

```java
@GetMapping("/api/v1/assets/{id}")
public ResponseEntity<ApiResponse<assetResponse>> getasset(@PathVariable long id) {
    return assetService.findById(id)
        .map(asset -> handleSuccessResponse(mapper.toResponse(asset)))
        .orElseGet(() -> handleErrorResponse(
            HttpStatus.NOT_FOUND, "asset not found with id " + id));
}
```

### GET (list/search) — Paginated Results

```java
@GetMapping("/api/v1/assets")
public ResponseEntity<ApiResponse<SearchResults<assetResponse>>> getassets(
        @Valid @ModelAttribute assetSearchRequest request) {

    Page<assetDomain> page = assetService.search(request.toDomain());

    return handleSuccessResponse(new SearchResults<>(
        request.limit(),
        request.offset(),
        page.getTotalElements(),
        page.getContent().stream().map(mapper::toResponse).toList()));
}
```

### POST — Create a Resource

**Normative Rule**: Successful creation MUST return `201 Created` via `handleCreatedResponse(data)`.

```java
@PostMapping("/api/v1/contacts")
public ResponseEntity<ApiResponse<ContactResponse>> createContact(
        @Valid @RequestBody CreateContactRequest request) {

    ContactDomain created = contactService.create(mapper.toDomain(request));
    return handleCreatedResponse(mapper.toResponse(created));
}
```

### PUT — Full Update

```java
@PutMapping("/api/v1/contacts/{id}")
public ResponseEntity<ApiResponse<ContactResponse>> updateContact(
        @PathVariable long id,
        @Valid @RequestBody UpdateContactRequest request) {

    return contactService.update(id, mapper.toDomain(request))
        .map(updated -> handleSuccessResponse(mapper.toResponse(updated)))
        .orElseGet(() -> handleErrorResponse(
            HttpStatus.NOT_FOUND, "Contact not found with id " + id));
}
```

### DELETE — Remove a Resource

```java
@DeleteMapping("/api/v1/contacts/{id}")
public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable long id) {
    if (!contactService.delete(id)) {
        return handleErrorResponse(HttpStatus.NOT_FOUND, "Contact not found with id " + id);
    }
    return handleDeletedResponse();   // 204 No Content
}
```

> ⚠️ **GAP-API-001 — OpenAPI**: `springdoc-openapi-starter-webmvc-ui` is not on the classpath, so there is no `/v3/api-docs` and no Swagger UI. Once adopted, every endpoint MUST declare `@Operation(summary = …)` and an `@ApiResponses` block covering each expected status. The generated spec is what the frontend type-generation step consumes (see [frontend-patterns.md STD-FE-008](frontend-patterns.md)).
>
> Camel's REST DSL contributes its own OpenAPI document via `camel-openapi-java` — a separate artifact from springdoc. If both are adopted they must be mounted on different paths.

---

## STD-API-003 — Request/Response Type Naming

**Normative Rule**: Request and response types MUST follow the naming patterns in the table below and MUST be Java `record`s (immutable, no Lombok needed). Paginated responses MUST use `SearchResults<T>` and include `limit`, `offset`, `totalRecords`, and `results`. They MUST live in `web/<feature>/dto/`.

### Naming Conventions

| Type | Naming pattern | Example |
|------|---------------|---------|
| Single-entity response | `[Entity]Response` | `assetProfileResponse` |
| List-item response | `[Entity]Response` | `assetResponse` |
| Paginated list response | `SearchResults<[Entity]Response>` | `SearchResults<assetResponse>` |
| Search/filter request | `[Entity]SearchRequest` | `assetSearchRequest` |
| Create request | `Create[Entity]Request` | `CreateContactRequest` |
| Update request | `Update[Entity]Request` | `UpdateContactRequest` |

### Paginated Response Structure

```java
// web/common/dto/SearchResults.java
public record SearchResults<T>(
    int limit,
    int offset,
    long totalRecords,
    List<T> results
) {}
```

**DTO Guidelines**:
- Use `record`, not a Lombok `@Data` class — records are immutable and serialize cleanly with Jackson
- Include only the fields needed for the operation
- Never expose internal database IDs or implementation details beyond what the operation needs
- Property names are camelCase in Java and camelCase on the wire — no `@JsonProperty` renaming unless integrating with a fixed external contract
- Never return a JPA `@Entity` from a controller

---

## STD-API-004 — Error Responses

### Error Format (Custom Envelope — NOT RFC 7807)

**Normative Rule**: All error responses MUST use the `ApiResponse`/`ApiError` envelope. Spring's `ProblemDetail` / `application/problem+json` format is NOT used in this project.

```json
{
  "data": null,
  "errors": [
    {
      "code": 404,
      "message": "asset not found with id 123"
    }
  ]
}
```

```java
// web/common/dto/ApiResponse.java
public record ApiResponse<T>(T data, List<ApiError> errors) {
    public static <T> ApiResponse<T> ok(T data)         { return new ApiResponse<>(data, List.of()); }
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(null, List.of(new ApiError(code, message)));
    }
}

// web/common/dto/ApiError.java
public record ApiError(int code, String message) {}
```

> **Why not `ProblemDetail`?** Spring Boot 3 ships it and it is a perfectly good standard. This project deliberately uses a single envelope for both success and failure so the frontend has exactly one response shape to unwrap. Mixing the two (200 → bare body, 4xx → `problem+json`) is what the envelope avoids. Pick one; this project picked the envelope. Do not introduce `ProblemDetail` alongside it.

### Returning Errors

Controllers MUST use `handleErrorResponse` from `BaseController`:

```java
// 404
return handleErrorResponse(HttpStatus.NOT_FOUND, "asset not found with id " + id);

// 400 — validation is auto-handled; MethodArgumentNotValidException is mapped
//       to a 400 ApiResponse by the @RestControllerAdvice. Do not check manually.

// 409 Conflict
return handleErrorResponse(HttpStatus.CONFLICT, "Contact already linked to asset");
```

Unhandled exceptions are caught by the `@RestControllerAdvice` and formatted as an `ApiResponse` with code 500.

### Status Code Reference

| Code | When | Source |
|------|------|--------|
| 200 OK | Successful GET, PUT, PATCH | `handleSuccessResponse()` |
| 201 Created | Successful POST | `handleCreatedResponse()` |
| 204 No Content | Successful DELETE | `handleDeletedResponse()` |
| 400 Bad Request | Bean Validation failure (auto) | `MethodArgumentNotValidException` handler |
| 401 Unauthorized | Missing/invalid bearer token | Spring Security (not yet present — GAP-ARC-006) |
| 403 Forbidden | Authenticated, insufficient authority | Spring Security (not yet present) |
| 404 Not Found | Resource not found | `handleErrorResponse(NOT_FOUND, …)` |
| 409 Conflict | Duplicate or constraint violation | `handleErrorResponse(CONFLICT, …)` |
| 500 Internal Server Error | Unhandled exception | `@RestControllerAdvice` fallback |
| 502 Bad Gateway | Upstream HTTP call returned an error status | Camel `onException(HttpOperationFailedException.class)` |
| 503 Service Unavailable | Retries exhausted against a flaky dependency | Camel `onException(...).maximumRedeliveries(3)` fallback |

> The 502 and 503 rows are live behaviour in `GithubRoute` and `RetryRoute`. Camel routes produce their own JSON bodies and never reach the `@RestControllerAdvice` — keep their error shape consistent with the envelope above.

---

## STD-API-005 — Pagination

**Normative Rule**: All list and search endpoints MUST support `limit` and `offset` query parameters. Default `limit` is **100**; maximum is **500**, enforced by `@Min(1) @Max(500)` on the request record. Responses MUST include `totalRecords`, `limit`, and `offset`.

**Default**: `limit = 100`, `offset = 0`
**Maximum**: 500 records

```java
public record assetSearchRequest(
    String term,
    @Min(1) @Max(500) Integer limit,
    @Min(0) Integer offset,
    String sortBy,
    String sortDirection
) {
    public assetSearchRequest {
        limit  = limit  == null ? 100 : limit;
        offset = offset == null ? 0   : offset;
    }
}
```

**Query parameters**:

```
GET /api/v1/assets?limit=50&offset=100&sortBy=name&sortDirection=asc
```

**Response fields**: `limit`, `offset`, `totalRecords`, `results`.

> **Why offset and not `Pageable`?** Spring Data's `Pageable` binds `page`/`size`, not `limit`/`offset`. Convert at the service boundary — `PageRequest.of(offset / limit, limit, sort)` — so the wire contract stays `limit`/`offset` while the repository still gets a `Pageable`. Note that this requires `offset` to be a multiple of `limit`; if arbitrary offsets are needed, use a `Specification` with an explicit `setFirstResult`/`setMaxResults` query instead.

---

## STD-API-006 — Query Parameters

**Normative Rule**: Search/filter parameters MUST be bound to a single request record via `@ModelAttribute` (not a long list of `@RequestParam` arguments), and that record MUST carry `@Valid`. Complex search supports a full-text term, pagination (`limit`, `offset`), and sort options (`sortBy`, `sortDirection`).

```java
@GetMapping("/api/v1/assets")
public ResponseEntity<ApiResponse<SearchResults<assetResponse>>> getassets(
        @Valid @ModelAttribute assetSearchRequest request) { … }
```

**Anti-Pattern**: eight `@RequestParam(required = false)` arguments on one method. Bind a record instead — it validates as a unit and gives the OpenAPI generator a named schema.

---

## API Endpoint Checklist

When creating a new endpoint:
- [ ] Route follows `/api/v1/[resource]` or `/api/v1/[resource]/[action]`
- [ ] HTTP method matches the operation (GET/POST/PUT/DELETE)
- [ ] Mapping annotation is on the method, not the class
- [ ] Handler method name includes the entity type (`getassetProfile`, not `get`)
- [ ] Controller extends `BaseController` (STD-BE-002)
- [ ] Request record carries Jakarta Bean Validation constraints, and the parameter carries `@Valid`
- [ ] Response uses `handleSuccessResponse()`, `handleCreatedResponse()`, or `handleDeletedResponse()`
- [ ] Not-found case returns `handleErrorResponse(HttpStatus.NOT_FOUND, message)`
- [ ] No `try`/`catch` for generic error handling — the `@RestControllerAdvice` owns that
- [ ] A MapStruct mapper exists for request → domain and domain → response
- [ ] Collection endpoints include pagination (`limit`, `offset`, `totalRecords`)
- [ ] No `@Entity` type appears in the method signature
- [ ] `@Operation` / `@ApiResponses` declared (once springdoc lands — GAP-API-001)
- [ ] `@PreAuthorize` declared (once Spring Security lands — GAP-ARC-006)

**Minimal compliant endpoint skeleton**:

```java
@RestController
public class assetController extends BaseController {

    private final assetService assetService;
    private final assetWebMapper mapper;

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

---

## AI Implementation Hints

- **Route prefix**: Always `/api/v1/` on the method — never a class-level `@RequestMapping`.
- **Camel is different**: `/camel/*` routes follow STD-API-007, not STD-API-001. Don't "fix" them to `/api/v1`.
- **Error envelope**: Use `handleErrorResponse()` — NOT `ResponseEntity.badRequest()`, NOT `ProblemDetail`, NOT `@ResponseStatus`.
- **Pagination default**: `limit = 100`, maximum 500, enforced with `@Min(1) @Max(500)`.
- **DTOs are records**: not Lombok `@Data` classes, not JPA entities.
- **Validation**: 400s are produced by the global handler from `MethodArgumentNotValidException` — no manual validation calls in controllers.
- **OpenAPI**: springdoc is NOT on the classpath yet (GAP-API-001) — do not add `@Operation` annotations that nothing reads unless the story is "adopt springdoc".
