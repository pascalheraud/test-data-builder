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

## For Claude Code users

- **Using this library in your own project?** Copy `src/claude/test-data-builder-usage/`
  into your project — see below. It covers dependency setup, GitHub Packages
  auth, and how to map your schema to `Table`/`Column`/`Data`.
- **Changing this repo's own code?** Load `test-data-builder-library` (in
  this repo's `.claude/skills/`, active while working in this repo) — the
  decisions behind this codebase (versioning, the bookstore example,
  database-vendor support, API conventions).

`test-data-builder-usage` lives under `src/claude/`, not `.claude/skills/`,
on purpose: it documents how *other* projects consume the published
artifact, not how to work on this repo's own code, so it has no reason to
auto-load while developing this library itself. `src/claude/` is the
canonical copy a consuming project copies from.

### Making the skill available in a consuming project

A project that only depends on `ovh.heraud:testdatabuilder` at build time
doesn't get the skill for free — nothing about a Maven/Gradle dependency
pulls in `src/claude/`.

**Simplest: copy `src/claude/test-data-builder-usage/` into the consuming
project's own `.claude/skills/`.** No git plumbing, nothing to initialize
on every clone — just a plain copy of the folder, versioned as part of the
consuming project itself. The tradeoff is staleness: if this skill changes
later, the copy doesn't update itself, so re-copy it occasionally (e.g.
when bumping the `testdatabuilder` dependency version).

If instead you want the copy to stay in sync automatically, this is also
possible via a **git submodule with a partial clone + sparse-checkout**
scoped to just `src/claude/test-data-builder-usage` (and `USERGUIDE.md`,
which the skill links to) — avoids pulling in this repo's Java source,
build files, and tests. More setup than it's worth for most projects; reach
for it only if drift between the copy and this repo's skill is a real
recurring problem.

## Contributing

Comments, questions, and bug reports are welcome as
[issues](https://github.com/pascalheraud/test-data-builder/issues). If you're
thinking about a pull request, open an issue first to discuss the change —
it's much easier to agree on the approach before code gets written than
after.

## License

GPL-3.0 — see [LICENSE](LICENSE).
