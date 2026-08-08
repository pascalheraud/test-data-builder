# Plan: Bootstrap the TestDataBuilder library (Issue #1)

Implements [spec.md](./spec.md). The generic classes below (`TestDataBuilder`,
`Data`, `TestTable`, `TestColumn`, `SqlExpression`) implement the model
documented in [[backend-java-test-data-builder]] exactly as specified there
— nothing about the model is invented or redesigned here, except the one
extension point the spec calls for: generated-id retrieval becomes
vendor-neutral instead of assuming `RETURNING id`, mirroring the pattern
NativSQL already uses for the same problem (see `DatabaseDialect
.getGeneratedKey(Map<String,Object> keys, String idColumn)` in
`nativsql-core`, overridden per vendor in `nativsql-mariadb`/`nativsql-oracle`).

## Package layout

```
ovh.heraud.testdatabuilder
├── generic/            # zero project knowledge, see backend-java-test-data-builder skill
│   ├── TestDataBuilder.java
│   ├── Data.java
│   ├── TestTable.java
│   ├── TestColumn.java
│   └── SqlExpression.java
└── (test sources only, not shipped in the jar)
    bookstore/           # example project-specific builder, under src/test
    ├── BookstoreTable.java
    ├── PublisherColumn.java
    ├── BookColumn.java
    ├── CustomerColumn.java
    ├── OrderColumn.java
    ├── OrderItemColumn.java
    ├── WarehouseColumn.java
    ├── OrderEventColumn.java
    ├── BookstoreTestDataBuilder.java
    └── OrderWithItem.java   # cluster record
```

`groupId`: `ovh.heraud`, `artifactId`: `testdatabuilder`, root package
`ovh.heraud.testdatabuilder`.

## Generic classes (as specified by the skill, not redesigned)

Package `ovh.heraud.testdatabuilder.generic`:

### `TestTable`
```java
public interface TestTable {
    String getSqlName();
    TestColumn[] getColumns();
}
```

### `TestColumn`
```java
public interface TestColumn {
    String getSqlName();
    default TestDataBuilder.TargetTypeEnum targetType() {
        return TestDataBuilder.TargetTypeEnum.TRANSPARENT;
    }
}
```

### `SqlExpression`
```java
public record SqlExpression(String sql, Object... params) {}
```

### `Data`
Fields: `table` (`TestTable`), `index` (`int`, table-scoped creation index),
`columns` (`LinkedHashMap<String, Object>`), `generatedId` (`Long`),
`added` (`boolean`).
Methods: `setColumn(String|TestColumn, Object)`, `getTable()`, `getIndex()`,
`getColumns()`, `getColumn(String|TestColumn)`, `getGeneratedId()`,
package-private `setGeneratedId(Long)`, `isAdded()`, package-private
`markAdded()`.

### `TestDataBuilder`
Abstract base, constructed with a `DataSource` (wrapped in a
`JdbcTemplate`). State: `orderedData` (`List<Data>`, global insertion
order), `toDeleteTables` (`LinkedHashSet<TestTable>`, first-touched order),
`currentByTable` (`Map<TestTable, Data>`), `dataNaming`
(`Map<String, Map<TestTable, List<Data>>>`), `nextIndexByTable`
(`Map<TestTable, Integer>`), `name` (current naming scope, default
`"root"`).

Public/protected API:
- `TargetTypeEnum { TRANSPARENT, STRING, DATE, TIMESTAMP }`
- `setDataColumn(Data, TestColumn, Object)` — transparent by default,
  override point for project-specific value conversion
- `withName(String)` — switches the current naming scope
- `register(Data)` (protected) — adds to `orderedData`, `currentByTable`,
  `dataNaming`
- `deleteTable(TestTable)` (protected) — marks a table for cleanup without
  any `Data` behind it
- `nextIndex(TestTable)` (protected) — per-table counter, 0-based
- `newData(TestTable)` (protected) — `new Data(table, nextIndex(table))`
- `current(TestTable)` (protected) — most recently registered `Data` for a
  table, throws if none
- `getData(String name, TestTable)` / `getDataList(String name, TestTable)`
- `apply()` = `delete()` + `create()`
- `delete()` — `DELETE FROM <table>` for every table in `toDeleteTables`,
  reverse of first-touched order
- `withDeleteAll(TestTable...)` — replaces `toDeleteTables` outright
- `create()` — inserts every `Data` in `orderedData` not yet `isAdded()`,
  in order; safe to call repeatedly
- `insert(Data)` (private) — builds a plain `INSERT INTO ... VALUES (...)`
  (no `RETURNING` clause), resolving `Data`-valued columns to their
  `generatedId` and `SqlExpression`-valued columns to inlined SQL + extra
  bind params. Executes via `JdbcTemplate.update(PreparedStatementCreator,
  KeyHolder)` with `Statement.RETURN_GENERATED_KEYS` (Spring's
  `GeneratedKeyHolder`) instead of parsing a `RETURNING` result column —
  this is the JDBC-standard mechanism and works unmodified across
  PostgreSQL, MariaDB, MySQL and Oracle. Resolves the id from
  `keyHolder.getKeys()` via `resolveGeneratedKey(Map<String, Object> keys)`,
  stores it on the `Data`, marks it added, and adds the table to
  `toDeleteTables`.

### Generated-id extension point (new — not in the original ported code)

Mirrors NativSQL's `DatabaseDialect.getGeneratedKey(Map<String,Object>
keys, String idColumn)`: a single protected method on `TestDataBuilder`
that a project's subclass overrides only if its vendor needs it.

```java
protected Long resolveGeneratedKey(Map<String, Object> keys) {
    return ((Number) keys.get("id")).longValue(); // PostgreSQL, MySQL, Oracle default
}
```

- **PostgreSQL, MySQL**: default implementation works unmodified — both
  return the key under the `"id"` entry.
- **MariaDB**: override returns `keys.get("insert_id")`, matching
  `MariaDBDialect.getGeneratedKey` exactly.
- **Oracle**: override checks the uppercase key first
  (`keys.get("ID")`), falling back to `keys.get("id")`, matching
  `OracleDialect.getGeneratedKey` exactly.

These three overrides live in the example project's vendor-specific test
builders (see "Tests" below), not in the generic package — the generic
class only exposes the single hook.

## Build (Gradle)

- `java-library` + `maven-publish` plugins.
- Java toolchain: 21 (current LTS).
- `group = "ovh.heraud"`, `version = "1.0.0"`.
- Dependencies: `api("org.springframework:spring-jdbc:<version>")` — the
  only runtime dependency, needed for `JdbcTemplate`.
- Test dependencies pull NativSQL's own per-vendor Testcontainers base
  classes instead of declaring Testcontainers modules directly — each
  reads from GitHub Packages:
  ```groovy
  repositories {
      maven {
          url = "https://maven.pkg.github.com/pascalheraud/nativsql"
          credentials {
              username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
              password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
          }
      }
  }
  dependencies {
      testImplementation "ovh.heraud:nativsql-postgres-test-fixtures:<version>"
      testImplementation "ovh.heraud:nativsql-mariadb-test-fixtures:<version>"
      testImplementation "ovh.heraud:nativsql-mysql-test-fixtures:<version>"
      testImplementation "ovh.heraud:nativsql-oracle-test-fixtures:<version>"
      testImplementation "org.junit.jupiter:junit-jupiter"
      testImplementation "org.assertj:assertj-core"
  }
  ```
  Reading from GitHub Packages requires the same `GITHUB_ACTOR`/
  `GITHUB_TOKEN` credentials as publishing — already available in CI, and
  a developer's local Gradle needs a personal access token with
  `read:packages` configured once (documented in the user guide's
  contributing section, not end-user-facing).
- `publishing { publications { maven(MavenPublication) { from(components["java"]) } } }`
  with the GitHub Packages repository for this GitHub repo
  (`https://maven.pkg.github.com/pascalheraud/test-data-builder`),
  credentials from `GITHUB_ACTOR`/`GITHUB_TOKEN` env vars (matches what the
  CI workflow provides — no separate secret to configure).
- Gradle wrapper committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`),
  Gradle 8.10.

## Example domain: bookstore (`src/test` only, not shipped)

Tables, each an enum constant of `BookstoreTable implements TestTable`,
with a matching `XxxColumn implements TestColumn` enum:

| Table | Columns |
|---|---|
| `PUBLISHER` | `id`, `name`, `country` |
| `BOOK` | `id`, `title`, `isbn`, `price`, `publisher_id` (FK → `PUBLISHER`) |
| `CUSTOMER` | `id`, `email`, `full_name`, `loyalty_points` |
| `ORDER` | `id`, `customer_id` (FK → `CUSTOMER`), `status`, `placed_at` |
| `ORDER_ITEM` | `id`, `order_id` (FK → `ORDER`), `book_id` (FK → `BOOK`), `quantity`, `unit_price` |
| `WAREHOUSE` | `id`, `name`, `location` (PostGIS point) |
| `ORDER_EVENT` | `id`, `order_id` (FK → `ORDER`), `event_type`, `occurred_at` — written by application code in a real system, **never seeded by a template**; exists purely to demonstrate `deleteTable()` |

`BookstoreTestDataBuilder extends TestDataBuilder`, one template per
pattern from the skill:

- **Base templates**: `newPublisher()`, `newCustomer()`, `newWarehouse()`
  — each uses `nextIndex`/`newData` to derive distinct defaults (indexed
  email, indexed ISBN, alternating status enum, etc.), per the skill's
  per-value-shape rules.
- **"For current" shortcut**: `newBookForCurrentPublisher()` — reads
  `current(BookstoreTable.PUBLISHER)`.
- **Cluster template**: `newOrderWithItem()` → `record OrderWithItem(Data
  order, Data item)`, composed from `newOrder()` /
  `newOrderItemForCurrentOrder()` (which itself references
  `current(BOOK)`).
- **Partial template**: `setCustomerAsLoyal(Data customer)` — fills a
  fixed `loyalty_points` value + a `status = "GOLD"`-style column on an
  already-created `Data`.
- **Parameterized template**: `newBook(String title, BigDecimal price)`,
  delegating to `newBook()` for the rest of the defaults.
- **`SqlExpression`**: `newWarehouse()` sets `location` via
  `new SqlExpression("ST_MakePoint(?, ?)", lon, lat)`.
- **`deleteTable()` override**: `delete()` override calls
  `deleteTable(BookstoreTable.ORDER_EVENT)` last (so it's removed first,
  before `ORDER`) then `super.delete()`.

Schema for the example: one script per vendor, since column types and
auto-increment syntax differ —
`src/test/resources/bookstore-schema-postgres.sql`,
`-mariadb.sql`, `-mysql.sql`, `-oracle.sql` — same tables and columns
listed above in each. `WAREHOUSE.location` is PostGIS-specific
(`geometry(Point, 4326)`, requires `CREATE EXTENSION postgis`) and only
meaningful on the PostgreSQL schema; the other three vendor schemas store
it as a plain `latitude`/`longitude` pair of numeric columns instead of a
geometry type — `newWarehouse()`'s `SqlExpression` usage stays PostgreSQL-
only (it is, after all, demonstrating a Postgres-specific raw-SQL escape
hatch), and the other vendors' `newWarehouse()` sets the two plain columns
directly. This is a deliberate, minor divergence between vendor test
builders, not a gap in coverage — every vendor still exercises every other
template pattern identically.

## Tests (comprehensive — one test class per concern, run against every supported vendor)

### Fixture: reuse NativSQL's containers, don't build new ones

Each vendor gets one abstract base test class in
`src/test/java/.../bookstore/<vendor>/`, extending NativSQL's own
`testFixtures` base class for that vendor instead of configuring
Testcontainers from scratch:

| Vendor | Extends (from `nativsql-<vendor>-test-fixtures`) |
|---|---|
| PostgreSQL | `ovh.heraud.nativsql.repository.postgres.PostgresBaseRepositoryTest` |
| MariaDB | `ovh.heraud.nativsql.repository.mariadb.MariaDBBaseRepositoryTest` |
| MySQL | `ovh.heraud.nativsql.repository.mysql.MySQLBaseRepositoryTest` |
| Oracle | `ovh.heraud.nativsql.repository.oracle.OracleBaseRepositoryTest` |

These base classes already provide: container creation and caching (keyed
by vendor + version + schema hash, so the same container is reused across
test classes instead of one per class), schema loading from a classpath
script (`getScriptPath()` returns the matching
`bookstore-schema-<vendor>.sql`), and a ready `DataSource`
(`getDataSource()`). Each vendor's abstract base here only implements
`getScriptPath()` and constructs the vendor's `BookstoreTestDataBuilder`
subclass (the one overriding `resolveGeneratedKey` per the table above)
from `getDataSource()`.

Every test class below is written once, against a `BookstoreTestDataBuilder`
reference, and run for all four vendors via a small JUnit 5
`@ParameterizedTest`-free approach: **one abstract test class per concern
containing the actual `@Test` methods, one thin concrete subclass per
vendor** (`XxxTestPostgres`, `XxxTestMariaDb`, `XxxTestMySql`,
`XxxTestOracle`) that only supplies the vendor's base class. This mirrors
how NativSQL's own `*ContactInfoRepositoryTest`/`*UserRepositoryTest` are
duplicated one-per-vendor today.

- **`TestDataBuilderRegistrationTest`**
  - `register()` makes the row retrievable via `getData`/`getDataList` under
    the current name
  - `withName(...)` scopes subsequent registrations to a new name;
    switching back reuses the earlier name's list
  - `getData` throws `IllegalStateException` when zero or more than one
    row exists for a name/table
  - `current(table)` throws when nothing has been registered for that
    table yet; returns the most recently registered row afterward,
    independent of naming scope

- **`TestDataBuilderIndexTest`**
  - `nextIndex(table)` returns 0, 1, 2, ... per call, independently per
    table
  - two calls to the same base template (e.g. `newPublisher()` twice)
    produce two rows with distinct derived defaults (distinct email/ISBN/
    etc.) and no unique-constraint violation on `create()`

- **`TestDataBuilderCreateTest`**
  - `create()` inserts every registered row and assigns a non-null
    `generatedId` to each, on every vendor — proving `resolveGeneratedKey`'s
    default and each vendor override actually reads back the right key
  - a `Data` referencing another `Data` as a column value is inserted
    with the referenced row's `generatedId` bound, and only after the
    referenced row itself has been inserted (FK ordering holds even when
    rows are registered in creation-dependency order but interleaved with
    unrelated tables)
  - calling `create()` a second time after registering more `Data` only
    inserts the newly registered rows — previously inserted rows are
    untouched (`isAdded()` stays `true`, no duplicate insert, no error)
  - a column set to a `SqlExpression` produces the expected inlined SQL
    with its own bind params, verified by reading the row back (e.g.
    `ST_X(location)`/`ST_Y(location)` round-trips the coordinates passed
    in) — this specific assertion runs on PostgreSQL only, since
    `SqlExpression` is demonstrated there via the PostGIS column (see
    "Example domain" above); the other three vendors still cover
    `SqlExpression`'s general mechanism (inlined SQL + extra bind params)
    with a vendor-neutral expression, e.g. `UPPER(?)` on a text column

- **`TestDataBuilderDeleteTest`**
  - `delete()` removes rows from every table touched, in reverse
    first-touched order (verified with a parent/child FK pair: deleting
    parent-first would throw, so the test proves child-first happens)
  - `deleteTable(table)` marks a table for cleanup with no `Data` behind
    it; calling it after other templates makes it the most-recently
    touched entry, so it is deleted **before** the table it structurally
    depends on (mirrors the `ORDER_EVENT` example)
  - `withDeleteAll(...)` fully replaces whatever was previously tracked,
    and `delete()` afterward respects the explicit order passed in
  - `apply()` = `delete()` then `create()`: rows from a previous `apply()`
    are gone before the new batch is inserted, verified by row counts

- **`BookstoreTemplateTest`** (exercises the example builder end to end,
  doubling as the "does every documented pattern actually work" check)
  - `newPublisher()` / `newCustomer()` / `newWarehouse()` insert with the
    expected distinct, index-derived defaults
  - `newBookForCurrentPublisher()` links to the last created publisher;
    switching to a second publisher and calling it again links to the new
    one
  - `newOrderWithItem()` returns both created rows with non-null
    generated ids, and the item's `order_id`/`book_id` resolve to the
    correct rows after `create()`
  - `setCustomerAsLoyal(customer)` mutates the passed `Data` in place and
    the loyalty columns are present after insert
  - `newBook(title, price)` overrides exactly those two columns and
    leaves the rest at their base-template defaults
  - full scenario test: seed a publisher, a book, a customer, an order
    with an item, a warehouse, and simulate an `ORDER_EVENT` row inserted
    directly (bypassing the builder, as the app would); call `apply()`
    twice across two "scenarios" in the same test class and confirm no
    leftover rows/FK errors between them

## Changelog

`CHANGELOG.md`, Keep a Changelog format:

```
## [1.0.0] - <release date>
### Added
- TestDataBuilder generic core: TestDataBuilder, Data, TestTable, TestColumn, SqlExpression.
- Vendor-neutral generated-id retrieval (PostgreSQL, MariaDB, MySQL, Oracle — same vendors as NativSQL).
- Bookstore example project (test sources) demonstrating every template pattern, tested against all four vendors via NativSQL's test containers.
- Gradle build, published to GitHub Packages via GitHub Actions.
```

## User guide (`USERGUIDE.md`) and `README.md`

Both written marketing-first (problem → why raw SQL → how), per the spec.
Content outline:
1. The problem: seeding through entities/repositories couples test setup
   to the code under test.
2. The fix: raw SQL, no entities, works identically for repository tests
   and E2E tests.
3. Install: Gradle/Maven snippet pointing at GitHub Packages.
4. Five-minute walkthrough building the bookstore example's `PUBLISHER`/
   `BOOK` tables and templates, ending with `apply()`.
5. Reference section: full template pattern catalogue (base / for-current
   / cluster / partial / parameterized), naming, `SqlExpression`,
   `deleteTable`, lifecycle.
6. Database support section: which vendors are supported (same as
   NativSQL), and the one-method `resolveGeneratedKey` override needed for
   MariaDB/Oracle.

## GitHub Actions

`.github/workflows/publish.yml`, triggered `on: release: types: [published]`:
1. checkout
2. set up JDK 21 (Temurin)
3. `./gradlew build` (runs the full test suite, including Testcontainers —
   requires Docker, available on `ubuntu-latest` runners)
4. `./gradlew publish` with `GITHUB_TOKEN`/`GITHUB_ACTOR` env vars wired
   to the built-in `secrets.GITHUB_TOKEN` (has `packages: write` via the
   job's `permissions:` block, no extra PAT needed)

A second workflow, `.github/workflows/ci.yml`, triggered on push/PR to
build and test on every change (not just releases) — `./gradlew build`
only, no publish step.

## New skill

`.claude/skills/test-data-builder-library/SKILL.md` in this repo,
capturing:
- versioning starts at `1.0.0` with no pre-1.0 history to preserve, and
  why (first stable extraction, not an evolving pre-release)
- the bookstore is the one canonical example domain — future additions
  extend it (new table/template pattern) rather than introducing a second
  unrelated demo domain
- publishing is GitHub Packages only, via the `on: release` workflow —
  Maven Central is explicitly not set up
- reference to [[backend-java-test-data-builder]] for the model itself,
  which this repo implements
- supported database vendors are exactly NativSQL's (PostgreSQL, MariaDB,
  MySQL, Oracle) and why: reusing NativSQL's own test containers instead of
  a second set only works if the vendor list matches; adding a vendor here
  means NativSQL must support it first
- `resolveGeneratedKey` is the single per-vendor extension point,
  deliberately modeled on NativSQL's `DatabaseDialect.getGeneratedKey` —
  new vendor overrides should read that class's per-vendor implementations
  first rather than re-deriving the JDBC quirk from scratch

## Log

(empty — fill in only if an implementation-time discovery forces a
decision not already resolved above)
