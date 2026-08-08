# TestDataBuilder

**Stop seeding test data through the code you're trying to test.**

Every project ends up needing to put rows in a database before a repository
or end-to-end test can run. The obvious way — go through your own entities
and repositories — quietly wires your test setup to the exact code path the
test exists to catch bugs in. A broken repository, a schema drift, a subtle
mapping bug: the seeding step "succeeds" anyway, because it's using the same
broken code, and your test never notices.

TestDataBuilder seeds with raw SQL instead. No entity classes, no
repositories, no ORM. Your test data setup stays correct even when the code
under test is not — which is, after all, the point of a test.

## Why this, not just hand-rolled INSERTs

Raw SQL is the right idea; hand-writing it in every test is not. TestDataBuilder
gives you:

- **Templates** — one method per table, with sane, distinct defaults, so
  `newCustomer()` three times in a row never collides on a unique constraint.
- **Foreign keys that resolve themselves** — pass one row as another row's
  column value, and it's wired to the right generated id once both are
  inserted, in the right order.
- **One-call cleanup** — `apply()` deletes what a previous run left behind
  and inserts the new batch, respecting every foreign key automatically, in
  the right direction.
- **The same code for repository tests and end-to-end tests** — it only ever
  needs a `DataSource`.

## Install

```groovy
repositories {
    maven { url = "https://maven.pkg.github.com/pascalheraud/test-data-builder" }
}
dependencies {
    testImplementation "ovh.heraud:testdatabuilder:1.0.0"
}
```

## A taste of it

```java
public class BookstoreTestDataBuilder extends TestDataBuilder {

    public BookstoreTestDataBuilder(DataSource dataSource, DatabaseVendor vendor) {
        super(dataSource, vendor);
    }

    public Data newPublisher() {
        int index = nextIndex(BookstoreTable.PUBLISHER);
        return register(newData(BookstoreTable.PUBLISHER)
                .setColumn(PublisherColumn.NAME, "Publisher " + index));
    }

    public Data newBookForCurrentPublisher() {
        return register(newData(BookstoreTable.BOOK)
                .setColumn(BookColumn.PUBLISHER_ID, current(BookstoreTable.PUBLISHER)));
    }
}
```

```java
builder.newPublisher();
builder.newBookForCurrentPublisher();
builder.apply(); // deletes leftovers, inserts the new batch, wires the FK
```

Full walkthrough, template patterns, and the database-vendor extension
point: see [USERGUIDE.md](USERGUIDE.md).

## Supported databases

PostgreSQL, MariaDB, MySQL, Oracle — the same vendors as
[NativSQL](https://github.com/pascalheraud/nativsql), whose own
Testcontainers setup this library's test suite reuses.

## Repo conventions

If you're using Claude Code on this repo, `.claude/skills/test-data-builder-library/`
records the decisions behind this codebase (versioning, the bookstore
example, database-vendor support, API conventions) — load it before making
structural changes.

## Contributing

Comments, questions, and bug reports are welcome as
[issues](https://github.com/pascalheraud/test-data-builder/issues). If you're
thinking about a pull request, open an issue first to discuss the change —
it's much easier to agree on the approach before code gets written than
after.

## License

GPL-3.0 — see [LICENSE](LICENSE).
