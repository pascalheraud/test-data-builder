# TestDataBuilder User Guide

## The problem

Seeding a database for a repository or end-to-end test through your own
entities, ORM, or repositories couples test setup to the exact code the test
is meant to exercise. When that code has a bug — a broken mapping, a schema
drift, a repository method that silently does the wrong thing — the seeding
step doesn't fail loudly. It just seeds slightly wrong data, and the test
built on top of it passes or fails for the wrong reason. Worse, a bug in a
repository can mask itself: the same broken code both writes and reads the
test's fixture, so it's internally "consistent" and nothing looks wrong.

## The fix

TestDataBuilder inserts rows with raw SQL, built from a table name and a
column map — nothing that touches an entity class, an ORM mapping, or a
repository. Test data setup can't be broken by the code under test, because
it never calls into it. The same builder code works unmodified for a
repository test (using whatever `DataSource` your test framework already
manages) and for an end-to-end test (using a `DataSource` built directly
from a `Testcontainers` database), since both are, in the end, just a JDBC
`DataSource`.

## Install

```groovy
repositories {
    maven {
        url = "https://maven.pkg.github.com/pascalheraud/test-data-builder"
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
dependencies {
    testImplementation "ovh.heraud:testdatabuilder:1.0.0"
}
```

### GitHub Packages authentication

GitHub Packages requires authentication even to *read* a public package —
an anonymous request fails with `Username must not be null!`. Supply a
GitHub personal access token with `read:packages` scope, either as:

- **CI**: the built-in `GITHUB_TOKEN`/`GITHUB_ACTOR` (already available in
  GitHub Actions, no extra secret needed for a package published on the
  same account).
- **Local builds**: `gpr.user`/`gpr.key` in `~/.gradle/gradle.properties`
  (global, not per-project — don't put a token in this repo's own
  `gradle.properties` or commit it), or the `GITHUB_ACTOR`/`GITHUB_TOKEN`
  environment variables.

This library itself needs the same setup to build (its test suite depends
on NativSQL's `test-fixtures` artifacts from
`https://maven.pkg.github.com/pascalheraud/nativsql`) — one token, scoped
to read packages on the account both repos live under, covers both.

## Five-minute walkthrough

Say your project has `publisher` and `book` tables, `book.publisher_id`
referencing `publisher.id`.

### 1. One enum per table, one enum per table's columns

```java
public enum PublisherColumn implements TestColumn {
    ID("id"), NAME("name");
    private final String sqlName;
    PublisherColumn(String sqlName) { this.sqlName = sqlName; }
    public String getSqlName() { return sqlName; }
}

public enum BookColumn implements TestColumn {
    ID("id"), TITLE("title"), PUBLISHER_ID("publisher_id");
    private final String sqlName;
    BookColumn(String sqlName) { this.sqlName = sqlName; }
    public String getSqlName() { return sqlName; }
}

public enum MyTable implements TestTable {
    PUBLISHER("publisher", PublisherColumn.values()),
    BOOK("book", BookColumn.values());
    private final String sqlName;
    private final TestColumn[] columns;
    MyTable(String sqlName, TestColumn[] columns) { this.sqlName = sqlName; this.columns = columns; }
    public String getSqlName() { return sqlName; }
    public TestColumn[] getColumns() { return columns; }
}
```

### 2. A builder, one method per table

```java
public class MyTestDataBuilder extends TestDataBuilder {

    public MyTestDataBuilder(DataSource dataSource, DatabaseVendor vendor) {
        super(dataSource, vendor);
    }

    public Data newPublisher() {
        Data publisher = newData(MyTable.PUBLISHER);
        int index = publisher.getIndex();
        return register(publisher.setColumn(PublisherColumn.NAME, "Publisher " + index));
    }

    public Data newBookForCurrentPublisher() {
        Data book = newData(MyTable.BOOK);
        int index = book.getIndex();
        return register(book
                .setColumn(BookColumn.TITLE, "Book " + index)
                .setColumn(BookColumn.PUBLISHER_ID, current(MyTable.PUBLISHER)));
    }
}
```

`newData(table)` gives you a blank row tagged with a fresh, table-scoped
index — use it to make each call's defaults distinct (`"Publisher " + index`
never collides). `current(table)` gives you the most recently registered
row for that table — that's how `newBookForCurrentPublisher()` wires the
foreign key without the caller passing anything.

### 3. Use it in a test

```java
MyTestDataBuilder builder = new MyTestDataBuilder(dataSource, DatabaseVendor.POSTGRESQL);

Data publisher = builder.newPublisher();
Data book = builder.newBookForCurrentPublisher();
builder.apply();

// book.getGeneratedId() and publisher.getGeneratedId() are now populated
```

See [`apply()`, `delete()`, `create()`: which one to call](#apply-delete-create-which-one-to-call)
below for what each one does and when to reach for it individually instead
of `apply()`.

## `apply()`, `delete()`, `create()`: which one to call

`apply()` is `delete()` then `create()` — the right call for the normal
case (seed a scenario, then run it). The two halves exist separately for
the cases where combining them isn't what you want:

| Call | Deletes | Inserts | Use it alone when |
|---|---|---|---|
| `create()` | nothing | every registered `Data` not yet inserted | you know the tables are already clean (a fresh Testcontainers database, or a repository test relying on transaction rollback for isolation — see [below](#isolation-strategy-differs-by-test-level-not-by-testdatabuilder)) and don't want a no-op `DELETE` pass, or you're adding a second batch mid-test after `apply()` already ran once (see below) |
| `delete()` | every table this builder has touched, in the right order | nothing | you want to assert on an empty/cleaned-up state directly, without immediately reseeding |
| `apply()` | (via `delete()`) | (via `create()`) | the normal case — almost always this one |

**`create()` alone never deletes anything, on purpose.** Calling it doesn't
even look at what's in the database — it only tracks which `Data` it has
already inserted *in this builder instance*. This is what makes it safe to
register more `Data` after an `apply()` and call `create()` again on its
own: only the newly registered rows go in, nothing already there gets
touched, and nothing gets deleted first.

```java
builder.newPublisher();
builder.apply();          // deletes leftovers, inserts the publisher

Data book = builder.newBookForCurrentPublisher();
builder.create();         // NOT apply() — inserts only the new book,
                           // doesn't re-delete/re-insert the publisher
```

**`apply()` is safe to call more than once on the same builder**, and this
is the normal way to reuse one builder across several independent
scenarios in the same test class: seed scenario 1, `apply()`, assert; seed
scenario 2 (fresh `Data`, same builder instance), `apply()` again —
`delete()` clears scenario 1's rows first (rows already marked inserted by
the earlier `create()` are left alone by `create()` itself, but still get
deleted by `delete()`, since `delete()` doesn't care about that flag), then
`create()` inserts only scenario 2's rows. The two scenarios never coexist
in the database, and scenario 2 never has to know what scenario 1 seeded.

### Isolation strategy differs by test level, not by TestDataBuilder

Which mechanism actually gives you a clean slate between tests depends on
what's *wrapping* the test, not on TestDataBuilder itself — it just inserts
and deletes rows either way:

| Test level | What isolates one test from the next | Where `create()`/`apply()` fits |
|---|---|---|
| Repository test (Spring-managed transaction per test) | The transaction rolls back after the test — every row the test (and TestDataBuilder) inserted disappears automatically | `create()` alone is enough; a `delete()`/`apply()` pass would just be a no-op `DELETE` against tables the rollback is about to empty anyway |
| End-to-end test (real browser, real app, real commits) | Nothing rolls back — the browser and the app under test both commit for real | `apply()` at the top of each scenario is what provides isolation; skip it and the previous scenario's rows are still there |

Don't assume one mechanism when reusing a `TestDataBuilder` subclass across
both — check whether the test already gets rollback isolation from its
base class before deciding whether a redundant `delete()`/`apply()` pass is
needed.

## Template patterns

Beyond the base template (`newPublisher()`) and the "for current" shortcut
(`newBookForCurrentPublisher()`) above:

- **Cluster template** — creates several related rows in one call, returning
  a record holding every one of them:
  ```java
  public record OrderWithItem(Data order, Data item) {}

  public OrderWithItem newOrderWithItem() {
      Data order = newOrderForCurrentCustomer();
      Data item = newOrderItemForCurrentOrder();
      return new OrderWithItem(order, item);
  }
  ```
- **Partial template** — fills one recurring group of columns on an
  already-created row:
  ```java
  public Data setCustomerAsLoyal(Data customer) {
      return customer.setColumn(CustomerColumn.LOYALTY_POINTS, 1000);
  }
  ```
- **Parameterized template** — takes the values that vary often enough to be
  worth naming, instead of every call site chaining `setColumn()`:
  ```java
  public Data newBook(String title, String price) {
      return newBookForCurrentPublisher()
              .setColumn(BookColumn.TITLE, title)
              .setColumn(BookColumn.PRICE, new BigDecimal(price));
  }
  ```
  Prefer a plain `String`/`int` parameter over asking the caller to build a
  `BigDecimal`/`Duration`/etc. themselves — do that conversion once, inside
  the template.

## Naming: seeding more than one thing at once

`withName(String)` scopes subsequent registrations to a name (default
`"root"`) — useful when a test seeds two independent sets of data (e.g. two
customers, each with their own order) and needs to look each set up
separately afterward:

```java
builder.newCustomer();          // under "root"
builder.withName("second");
builder.newCustomer();          // under "second"

builder.getData("root", MyTable.CUSTOMER);   // the first one
builder.getData("second", MyTable.CUSTOMER); // the second one
builder.getData(MyTable.CUSTOMER);           // same as passing the current name
```

## Raw SQL for a column that needs it

Most columns bind a plain value. Some need a SQL expression instead — a
PostGIS point, a `CAST`, an enum type coercion:

```java
Data warehouse = builder.newWarehouse()
        .setColumn(WarehouseColumn.CODE, new SqlExpression("UPPER(?)", "wh-1"));
```

`sql()` is inlined in place of that column's placeholder; `params()` are
bound in its place — everything else about the row is unaffected.

## A table your templates never seed

Some tables are written by the application itself as a side effect of the
flow under test (an audit log, a usage-tracking table) — nothing ever calls
a template for them, so `delete()` doesn't know to clear them by default.
Mark them explicitly, and call it *last* so it's cleaned up *first*:

```java
@Override
public void delete() {
    deleteTable(MyTable.USAGE_LOG); // written by the app, never seeded
    super.delete();
}
```

## Converting a project-specific value type

`Data.setColumn` only ever stores what you hand it. If your project has its
own date-builder/money/whatever type that needs converting before it hits
JDBC, override `setDataColumn` and tag the relevant columns with a
`targetType()`:

```java
public enum OrderColumn implements TestColumn {
    PLACED_AT("placed_at") {
        public TargetTypeEnum targetType() { return TargetTypeEnum.TIMESTAMP; }
    };
    // ...
}

@Override
public Data setDataColumn(Data data, TestColumn column, Object value) {
    if (value instanceof MyDate myDate && column.targetType() == TargetTypeEnum.TIMESTAMP) {
        return data.setColumn(column, Timestamp.valueOf(myDate.toLocalDateTime()));
    }
    return super.setDataColumn(data, column, value);
}
```

## Reading back state your builder didn't write

`getJdbcTemplate()` gives direct access to the `JdbcTemplate` behind the
builder — for read-back assertions (`SELECT` a column the application under
test mutated) or one-off queries that don't fit the row-templating model.
Wrap the ones you use often in a named method on your builder
(`currentOrderStatus(Data order)`) rather than inlining SQL at every call
site — it reads better and survives a column rename in one place.

Don't reach for the builder to *simulate the application itself* writing a
row (e.g. inserting into that audit-log table `deleteTable` cleans up) —
that's usually clearer left as plain SQL directly in the test, with a
comment saying so, since it isn't seeding: it's standing in for the app.

## Database support

TestDataBuilder works against **PostgreSQL, MariaDB, MySQL, and Oracle**.
The only vendor-specific behavior is reading back the id a database just
generated for an inserted row — the JDBC driver's key map differs by vendor
— and it's handled entirely by the `DatabaseVendor` you pass to the
constructor:

```java
new MyTestDataBuilder(dataSource, DatabaseVendor.POSTGRESQL);
new MyTestDataBuilder(dataSource, DatabaseVendor.MARIADB);
new MyTestDataBuilder(dataSource, DatabaseVendor.MYSQL);
new MyTestDataBuilder(dataSource, DatabaseVendor.ORACLE);
```

No subclassing, no per-vendor override — the same builder class works
against all four just by passing the right enum value at construction time.

### Running your tests against a real database

TestDataBuilder only ever needs a `DataSource` — how you stand up the
database behind it is entirely up to you. If your tests already use
Testcontainers for one of these four vendors, you can reuse
[NativSQL](https://github.com/pascalheraud/nativsql)'s own
Testcontainers-based container setup (published as
`nativsql-<vendor>-test-fixtures`) instead of configuring Testcontainers
from scratch — this library's own test suite does exactly that.

Each vendor's `test-fixtures` artifact ships a `*BaseRepositoryTest` class
(e.g. `ovh.heraud.nativsql.repository.oracle.OracleBaseRepositoryTest`)
that handles container creation and caching (keyed by vendor, version, and
a hash of your schema script, so the same container is reused across test
classes instead of one per class), loads your schema from a classpath
resource, and exposes a ready `getDataSource()`. Extend it, point
`getScriptPath()` at your schema, and build your `TestDataBuilder` from
`getDataSource()` in a `@BeforeEach`:

```java
// this library's own OracleBookstoreTest, as a concrete example
public abstract class OracleBookstoreTest extends OracleBaseRepositoryTest {

    protected BookstoreTestDataBuilder builder;

    @Override
    protected String getScriptPath() {
        return "bookstore-schema-oracle.sql"; // classpath resource, in src/test/resources
    }

    @BeforeEach
    void createBuilder() {
        builder = new BookstoreTestDataBuilder(getDataSource(), DatabaseVendor.ORACLE);
    }
}
```

Every actual `@Test` class then just extends `OracleBookstoreTest` (or
whichever vendor) and uses the inherited `builder` field — see
`src/test/java/.../bookstore/oracle/` in this repo for the full set. Add
one such base class per vendor you test against
(`ovh.heraud.nativsql.repository.postgres.PostgresBaseRepositoryTest`,
`.mariadb.MariaDBBaseRepositoryTest`, `.mysql.MySQLBaseRepositoryTest`,
`.oracle.OracleBaseRepositoryTest`), each pulled in via the matching
`nativsql-<vendor>-test-fixtures` dependency and pointing at that vendor's
own schema script (column types and auto-increment syntax differ by
vendor, so one script per vendor is normal — see this repo's
`bookstore-schema-*.sql` files).

## Using TestDataBuilder for end-to-end tests

TestDataBuilder was designed to work identically for repository tests and
end-to-end tests — the only reason it works for both is that it never
depends on anything except a `DataSource`. For an E2E test (a real browser
driving the actual built app, against a real database), `apply()` at the
start of each scenario is what gives the scenario a known, isolated
starting state — there's no Spring-managed transaction to roll back for
you, since the browser and the app under test both commit for real.

```java
class PlaceOrderScenarioTest extends E2ETestBase {

    @Test
    void customerCanPlaceAnOrderAndSeeItInTheirHistory() {
        // Given a customer with a book available to order
        testData.newCustomer();
        testData.newPublisher();
        testData.newBookForCurrentPublisher();
        testData.apply();
        Data customer = testData.getData(BookstoreTable.CUSTOMER);
        Data book = testData.getData(BookstoreTable.BOOK);

        // When they place an order for that book
        Page page = browser.newContext().newPage();
        CatalogPage catalog = CatalogPage.open(page, baseUrl(), customer.getColumn("email"));
        OrderConfirmationPage confirmation = catalog.addToCart(book.getColumn("title")).checkout();

        // Then the order appears in their order history
        assertThat(confirmation.orderHistory()).contains(book.getColumn("title"));
    }
}
```

This mirrors [[test/e2e]]'s scenario/PageObject structure exactly — nothing
about using TestDataBuilder inside an E2E scenario differs from a
repository test, beyond `apply()` running against a database with no
transaction to undo it. In particular:

- **One scenario per test class**, `apply()` at the top of each `@Test` to
  seed that scenario's baseline, independent of any other scenario.
- **A flow that depends on a value only ever delivered by email** (a
  confirmation code, say) reads it back through the same `DataSource` your
  `TestDataBuilder` uses, from wherever the app persists outbound
  email/notifications as a side effect of sending — the same `getJdbcTemplate()`
  read-back approach described above, not a real mailbox.
- **A dependency the app calls out to** (a payment provider, a shipping
  quote API) gets mocked non-intrusively inside the app itself (e.g. a
  `@Primary` subclass wired ahead of the real bean), with the mock's calls
  traced to a table your `TestDataBuilder` subclass can read back the same
  way — never mocked from the test side.
- **A scenario that wants a fully clean slate regardless of what this
  particular test happens to seed** can call `withDeleteAll(...)` with an
  explicit table list before `apply()`, instead of relying on whatever
  `delete()` would infer from this test's own `newXxx()` calls:
  ```java
  testData.withDeleteAll(BookstoreTable.ORDER_ITEM, BookstoreTable.ORDERS,
          BookstoreTable.BOOK, BookstoreTable.PUBLISHER, BookstoreTable.CUSTOMER);
  testData.newCustomer();
  testData.apply();
  ```
  Useful for a scenario that only seeds a customer but still wants every
  table a *previous* scenario might have left rows in wiped first — a
  fuller reset than the normal "only what this builder touched" default,
  at the cost of spelling out the table list (and its FK-safe order)
  explicitly instead of letting it track itself.

See [[backend/java/test/playwright]] for the full Java/Testcontainers/Playwright
setup (stack layout, `E2ETestBase`, PageObject conventions, non-intrusive
mocking) this example builds on — this section only covers what's specific
to using `TestDataBuilder` inside that setup.

## For Claude Code users

**Using this library from another project?** Copy this repo's
`src/claude/test-data-builder-usage/` into your own project's
`.claude/skills/` — the condensed "what to write in your project" version
of this guide (install, GitHub Packages auth, mapping your schema to
`Table`/`Column`/`Data`). It lives outside `.claude/skills/` here since it's
not meant to auto-load while working on this repo's own code — see the
README's "For Claude Code users" section for why and how to copy it.

**Working on this repo's own code?** This repo's own
`.claude/skills/test-data-builder-library/` skill covers decisions specific
to *maintaining this codebase* (versioning, the bookstore example, vendor
support) — load it only when changing the library itself, not when
consuming it. Skills from the shared [claude](https://github.com/pascalheraud/claude)
skills repo cover the rest — load the ones relevant to what you're doing:

- `backend/java/test/playwright` — the Given/When/Then test structure used
  throughout this library's own test suite, and the full Java/
  Testcontainers/Playwright E2E stack (scenario classes, PageObjects,
  non-intrusive mocking) if you're writing E2E tests with this library.
- `test/e2e` — the framework-agnostic scenario/PageObject/snapshot
  conventions `backend/java/test/playwright` implements; load it if you
  want the reasoning behind those conventions, not just the Java mechanics.

## Contributing

See the [README](README.md#contributing).
