# Database Patterns

---

## Technical Context

| Item | Value | Status |
|------|-------|--------|
| **Database (dev)** | H2 2.x, in-memory (`jdbc:h2:mem:devdb;DB_CLOSE_DELAY=-1`) | ✅ Configured |
| **Database (prod)** | Undecided | ⚠️ [TECHNICAL_DECISIONS_PENDING.md](TECHNICAL_DECISIONS_PENDING.md) Decision 1 |
| **ORM** | Spring Data JPA + Hibernate 6.5 | ✅ Configured, no entities yet |
| **Dialect** | `org.hibernate.dialect.H2Dialect` (explicit) | ✅ |
| **Schema management** | `spring.jpa.hibernate.ddl-auto=update` | ⚠️ Dev-only — see STD-DB-004 |
| **Query filtering** | Hibernate `@SQLRestriction` (soft-delete) | ❌ Prescribed |
| **Migrations** | Flyway | ❌ Prescribed, not on classpath |
| **Console** | H2 console at `/h2-console`, unauthenticated | ⚠️ Dev-only, currently unconditional |

---

## STD-DB-001 — Standard Audit Columns

**Normative Rule**: Every table MUST include the following six standard columns. No table MAY be created or migrated without them.

| Column | Java type | SQL type | Required | Description | Default |
|--------|-----------|----------|----------|-------------|---------|
| `id` | `Long` | `bigint` identity | Yes | Primary key | generated |
| `is_deleted` | `boolean` | `boolean` | Yes | Soft-delete flag | `false` |
| `modified_date` | `Instant` | `timestamp(6)` | Yes | UTC timestamp of last update | now |
| `modified_by` | `String` | `varchar(64)` | Yes | Username of last modifier | current principal |
| `created_date` | `Instant` | `timestamp(6)` | Yes | UTC timestamp of creation | now |
| `created_by` | `String` | `varchar(64)` | Yes | Username of creator | current principal |

**Implementation Requirements**:
- All timestamps MUST be stored in UTC. Use `Instant`, not `LocalDateTime` — `LocalDateTime` has no zone and silently reinterprets itself when the JVM or database timezone changes.
- `is_deleted` defaults to `false`. Deletes are logical: set `is_deleted = true`; no row is physically removed.
- The soft-delete filter MUST be applied globally so callers never write `WHERE is_deleted = false` by hand (STD-DB-005).
- `modified_date` / `modified_by` MUST be updated on every write, including the write that sets `is_deleted = true`.
- `created_date` / `created_by` MUST be set on insert and MUST NOT change afterwards.
- Responses MAY expose audit fields as read-only when audit history is relevant to the user.

**Implementation — a shared `@MappedSuperclass`**:

```java
// repository/common/AuditableEntity.java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 64)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "modified_date", nullable = false)
    private Instant modifiedDate;

    @LastModifiedBy
    @Column(name = "modified_by", nullable = false, length = 64)
    private String modifiedBy;

    // getters/setters or Lombok @Getter/@Setter
}
```

`@CreatedDate` / `@LastModifiedDate` / `@CreatedBy` / `@LastModifiedBy` are Spring Data JPA auditing annotations. They do nothing without:

```java
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    AuditorAware<String> auditorProvider() {
        // Once Spring Security lands, read the authenticated principal instead.
        return () -> Optional.of("system");
    }
}
```

`updatable = false` on the created columns is what enforces "MUST NOT change afterwards" at the ORM level rather than by convention.

**Anti-Patterns**:
- ❌ `LocalDateTime` or `java.util.Date` for a timestamp — use `Instant`
- ❌ A table without the six columns
- ❌ A hard `DELETE` statement
- ❌ Setting `createdDate` manually in a service — auditing owns it
- ❌ `@EnableJpaAuditing` missing, so every audit column silently persists as `null` and the `nullable = false` constraint fails at insert

**Confidence**: Medium (prescriptive — no entity exists yet)

---

## STD-DB-002 — Naming Conventions

**Normative Rule**: All database object names MUST use `snake_case`. Table names MUST be singular. Names MUST be declared explicitly with `@Table(name = …)` and `@Column(name = …)` — never left to Hibernate's implicit naming strategy.

| Object | Pattern | Example |
|--------|---------|---------|
| Table | singular `snake_case` noun | `asset`, `user_session`, `audit_log` |
| Column | `snake_case` | `asset_name`, `email_address`, `naic_code` |
| Primary key | `id` (`bigint`, identity) | `id` |
| Foreign key column | `{referenced_table}_id` | `asset_id`, `user_id` |
| Index | `ix_{table}_{column(s)}` | `ix_asset_naic_code` |
| Unique constraint | `uq_{table}_{column(s)}` | `uq_asset_naic_code` |
| Check constraint | `ck_{table}_{column}_{description}` | `ck_asset_state_two_letter_code` |
| Foreign key constraint | `fk_{table}_{referenced_table}` | `fk_contact_asset` |

**Why explicit names**: Hibernate's `CamelCaseToUnderscoresNamingStrategy` would derive `asset_name` from `assetName` on its own — until someone renames the field, or upgrades Hibernate, or the strategy is overridden by a starter. An explicit `@Column(name = …)` makes the database contract independent of the Java field name, which is the whole point of having a migration file.

**Java side**: entity classes are `PascalCase` and singular with an `Entity` suffix (`assetEntity`), fields are `camelCase`. The mismatch with `snake_case` SQL is intentional and idiomatic in both languages.

```java
@Entity
@Table(
    name = "asset",
    indexes = @Index(name = "ix_asset_naic_code", columnList = "naic_code"),
    uniqueConstraints = @UniqueConstraint(name = "uq_asset_naic_code", columnNames = "naic_code"))
public class assetEntity extends AuditableEntity {

    @Column(name = "asset_name", nullable = false, length = 255)
    private String assetName;

    @Column(name = "naic_code", length = 10)
    private String naicCode;
}
```

**Anti-Patterns**:
- ❌ Plural table names (`assets`)
- ❌ Mixed casing (`assetName`, `assetname`) on any database object
- ❌ Relying on the implicit naming strategy
- ❌ An index or constraint with a generated name (`UK7a3b…`) — unnamable in a later migration
- ❌ A foreign key column that does not mirror the referenced table (`owner_id` pointing at `asset`)

**Confidence**: Medium (prescriptive)

---

## STD-DB-003 — Column Data Type Standards

**Normative Rule**: Column types MUST follow the conventions below. Deviations require an inline comment in the migration file.

**String fields**:

| Use case | Type | JPA |
|----------|------|-----|
| Email address | `varchar(255)` | `@Column(length = 255)` |
| Person / asset name | `varchar(255)` | `@Column(length = 255)` |
| Codes (NAIC, NCCI, …) | `varchar(10)` or an explicit length | |
| URL | `varchar(500)` | |
| Phone number (formatted) | `varchar(20)` | |
| Full address | `varchar(500)` | |
| City | `varchar(100)` | |
| State/region code | `varchar(2)` | |
| Enum name | `varchar(40)` | `@Enumerated(EnumType.STRING)` |
| Free-form / large text | `text` / `clob` | `@Lob` — use sparingly; always prefer an explicit length |

Every `String` field MUST declare a `length`. The JPA default is 255, which is a coincidence rather than a decision — declare it so the migration and the entity agree.

**Boolean fields**:
- SQL `boolean`, Java primitive `boolean` (not `Boolean` — a nullable flag has three states and every caller then has to handle the third)
- Name with an `is` prefix in SQL (`is_deleted`, `is_locked`); the Java field drops the prefix (`deleted`) because the getter is already `isDeleted()`
- Always `nullable = false` with an explicit default

**Timestamp fields**:
- SQL `timestamp(6)`; Java `Instant`
- Store all timestamps in UTC
- Suffix `Date` for a date-only value (`java.time.LocalDate`), `DateTime` or nothing for a full timestamp

Set the JDBC timezone explicitly so Hibernate does not convert through the JVM default:

```properties
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
```

**Numeric fields**:
- Primary keys: `bigint` identity → Java `Long` (the wrapper, so an unsaved entity's id is `null` rather than `0`)
- Foreign keys: match the referenced primary key type
- Currency / money: `numeric(18,2)` → Java `BigDecimal`. **Never `double` or `float` for money** — binary floating point cannot represent `0.10` exactly, and the error compounds across a sum.
- Percentages (0–100): `numeric(5,2)` → `BigDecimal`

**Anti-Patterns**:
- ❌ `double` / `float` / `Double` for a monetary or exact-decimal value
- ❌ `LocalDateTime` for an instant in time
- ❌ `text` / `@Lob` without a documented justification
- ❌ A boolean column without the `is_` prefix or without an explicit default
- ❌ `int` for a primary or foreign key — use `bigint`
- ❌ A `String` field with no declared `length`

**Confidence**: Medium (prescriptive)

---

## STD-DB-004 — Schema Management

**Normative Rule** (prescribed): Schema changes MUST be expressed as versioned Flyway migrations in `backend/src/main/resources/db/migration/`, named `V{n}__{snake_case_description}.sql`. `spring.jpa.hibernate.ddl-auto` MUST be set to `validate` in every environment except local dev.

> ⚠️ **GAP-DB-004**: the current setting is `spring.jpa.hibernate.ddl-auto=update` with no Flyway on the classpath. Hibernate derives the schema from the entities at startup. With an in-memory H2 that is rebuilt on every run, this is harmless and convenient. Against a persistent database it is dangerous: `update` adds columns and tables but never drops, never narrows, never renames, and never reorders — so the live schema silently diverges from what any migration file would produce, and the divergence is invisible until a deploy fails.

**Prescribed setup**:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

```properties
# application.properties  (all environments)
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

```sql
-- backend/src/main/resources/db/migration/V1__create_asset.sql
CREATE TABLE asset (
    id            BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    asset_name  VARCHAR(255) NOT NULL,
    naic_code     VARCHAR(10),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_date  TIMESTAMP(6) NOT NULL,
    created_by    VARCHAR(64)  NOT NULL,
    modified_date TIMESTAMP(6) NOT NULL,
    modified_by   VARCHAR(64)  NOT NULL,
    CONSTRAINT uq_asset_naic_code UNIQUE (naic_code)
);

CREATE INDEX ix_asset_naic_code ON asset (naic_code);
```

`ddl-auto=validate` is the point of the pair: Flyway owns the schema, and Hibernate refuses to start if the entities and the schema have drifted apart. That turns a silent mismatch into a startup failure.

**Rules once Flyway is adopted**:
- A migration that has been applied anywhere is **immutable**. Fix a mistake with a new `V{n+1}` migration; never edit an applied file (Flyway's checksum will reject it).
- One logical change per migration.
- Migration SQL must be valid for the target database. If dev is H2 and production is PostgreSQL, either use portable SQL or run H2 in PostgreSQL compatibility mode — otherwise migrations pass in CI and fail on deploy. This is a large part of why Decision 1 matters.

**Confidence**: Low (prescriptive — Flyway is not on the classpath)

---

## STD-DB-005 — Soft Delete

**Normative Rule**: Deletes MUST be logical. The service layer sets `is_deleted = true`; it never calls `repository.delete(...)`. The filter MUST be applied at the entity level with Hibernate's `@SQLRestriction` so no caller can forget it.

```java
@Entity
@Table(name = "asset")
@SQLRestriction("is_deleted = false")
@SQLDelete(sql = "UPDATE asset SET is_deleted = true, modified_date = CURRENT_TIMESTAMP WHERE id = ?")
public class assetEntity extends AuditableEntity { … }
```

- `@SQLRestriction` (Hibernate 6.3+; replaces the deprecated `@Where`) appends the predicate to **every** query for the entity, including collection loads and `findById`.
- `@SQLDelete` rewrites `repository.delete(entity)` into the update above, so even an accidental delete call soft-deletes.

**Two limits to know before relying on this**:

1. **`@SQLRestriction` does not apply to native queries.** A `@Query(nativeQuery = true)` sees soft-deleted rows unless it adds the predicate itself.
2. **Uniqueness and soft delete interact badly.** A `UNIQUE (naic_code)` constraint still counts soft-deleted rows, so recreating a previously deleted asset fails. Use a partial index where the database supports it:
   ```sql
   CREATE UNIQUE INDEX uq_asset_naic_code
       ON asset (naic_code) WHERE is_deleted = false;   -- PostgreSQL
   ```
   H2 does not support partial indexes, which is another dependency on Decision 1.

**Anti-Patterns**:
- ❌ `repository.deleteById(id)` in a service without `@SQLDelete` on the entity
- ❌ Adding `and isDeleted = false` by hand to a derived query when `@SQLRestriction` is present — the predicate is applied twice
- ❌ A native query that forgets the predicate
- ❌ An unconditional unique constraint on a soft-deleted table

**Confidence**: Low (prescriptive)

---

## STD-DB-006 — Dev-Only Settings

**Normative Rule**: The H2 console and SQL logging MUST be confined to a `dev` profile. They MUST NOT be reachable in any deployed environment.

> ⚠️ **GAP-DB-006**: both are currently unconditional in `application.properties`:
> ```properties
> spring.h2.console.enabled=true      # an unauthenticated SQL shell at /h2-console
> spring.jpa.show-sql=true            # writes to stdout, bypassing the logger entirely
> ```
> With no Spring Security on the classpath (GAP-ARC-006), `/h2-console` is an open, unauthenticated console over the whole database.

**Prescribed split**:

```properties
# application.properties — shared
spring.jpa.hibernate.ddl-auto=validate
```

```properties
# application-dev.properties
spring.datasource.url=jdbc:h2:mem:devdb;DB_CLOSE_DELAY=-1
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
spring.jpa.hibernate.ddl-auto=update
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.orm.jdbc.bind=TRACE
```

Prefer the two `logging.level` lines over `show-sql=true`: they route through Logback (so they respect the log pattern, the correlation ID, and the appender configuration) and `orm.jdbc.bind` shows bound parameter values, which `show-sql` does not.

**Anti-Patterns**:
- ❌ `spring.h2.console.enabled=true` outside a dev profile
- ❌ `spring.jpa.show-sql=true` anywhere — use the logger
- ❌ `ddl-auto=update` or `create-drop` against a persistent database

**Traceability**: `backend/src/main/resources/application.properties`

**Confidence**: High (accurately describes the current gap)

---

## AI Implementation Hints

- **Every table** needs the six audit columns from STD-DB-001. Inherit them from `AuditableEntity`; don't retype them.
- **`Instant`, never `LocalDateTime`.** `BigDecimal`, never `double`, for money.
- **Naming**: singular `snake_case` tables, `id` primary key, `{table}_id` foreign keys — declared explicitly with `@Table` / `@Column`, never left to the implicit strategy.
- **Enums**: `@Enumerated(EnumType.STRING)`. The ORDINAL default is the single most damaging default in JPA — see [backend-patterns.md STD-BE-011](backend-patterns.md).
- **Soft delete**: `@SQLRestriction("is_deleted = false")` + `@SQLDelete` on the entity. Remember it does not cover native queries.
- **Schema**: `ddl-auto=update` is current reality and dev-only. Do not point it at a persistent database (STD-DB-004).
- **Don't add Flyway speculatively** — add it with the first entity that outlives a JVM restart.
- **Queries**: `Specification` or `@Query` with named parameters. Never string-concatenated SQL.
- **Watch for N+1**: use `@EntityGraph` or `join fetch` when loading a collection you will iterate.
