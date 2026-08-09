---
name: test-data-builder-usage
description: How to consume the published TestDataBuilder library (ovh.heraud:testdatabuilder) from another project — dependency setup, GitHub Packages auth, and mapping your own schema to Table/Column/Data.
---

# Using TestDataBuilder (as a dependency)

This skill is for a project that **consumes** the published `ovh.heraud:testdatabuilder`
artifact. For the full model behind it (`TestDataBuilder`, `Data`,
`TestTable`, `TestColumn`, template patterns, naming, lifecycle), see
[USERGUIDE.md](../../../USERGUIDE.md) — this skill is the condensed version,
covering what's specific to wiring the real library into a consuming
project.

For decisions behind maintaining *this library's own codebase* (versioning,
the bookstore example, vendor support internals), see
[[test-data-builder-library]] instead — that one is for people changing the
library, not people using it.

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

GitHub Packages requires authentication even to read a public package. In
CI, the built-in `GITHUB_TOKEN`/`GITHUB_ACTOR` is enough. Locally, set
`gpr.user`/`gpr.key` in `~/.gradle/gradle.properties` (global, never the
consuming project's own committed `gradle.properties`) or the
`GITHUB_ACTOR`/`GITHUB_TOKEN` environment variables.

## Wiring your schema in

Three things a consuming project writes itself — none of them ship with the
library:

1. **One `TestColumn` enum per table**, one constant per column, mapping to
   the SQL column name.
2. **One `TestTable` enum**, one constant per table, pairing the SQL table
   name with that table's `TestColumn[]`.
3. **A `TestDataBuilder` subclass** with one `newXxx()` template method per
   table (plus `newXxxForCurrentYyy()`, cluster, partial, and parameterized
   variants as needed — see [USERGUIDE.md § Template patterns](../../../USERGUIDE.md#template-patterns)
   for when to reach for each).

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
}
```

```java
MyTestDataBuilder builder = new MyTestDataBuilder(dataSource, DatabaseVendor.POSTGRESQL);
builder.newPublisher();
builder.apply(); // delete() then create()
```

`DatabaseVendor` is one of `POSTGRESQL`, `MARIADB`, `MYSQL`, `ORACLE` — pick
whichever your `DataSource` actually points at; nothing else about the
builder subclass changes per vendor.

## Where the `DataSource` comes from

- **Repository test**: reuse whatever `DataSource` your test base class
  already manages — don't stand up a second container just for the builder.
- **E2E test**: build a plain `DataSource` from a `Testcontainers`
  `JdbcDatabaseContainer` — the same container your app under test is wired
  to. If you already depend on NativSQL's Testcontainers setup, its
  `nativsql-<vendor>-test-fixtures` artifacts expose a ready
  `getDataSource()` via a `*BaseRepositoryTest` base class — see the
  USERGUIDE's "Running your tests against a real database" section for the
  concrete extension shape.

## `apply()` vs `create()`/`delete()` alone

Default to `apply()`. Reach for `create()` alone only when the tables are
already known-clean (fresh Testcontainers database, or a repository test
that gets rollback isolation from its base class) or when adding a second
batch of `Data` mid-test after an earlier `apply()` already ran — `create()`
never deletes and only inserts rows not yet marked `added`, so it's safe to
call repeatedly. Full table in [USERGUIDE.md § apply/delete/create](../../USERGUIDE.md#apply-delete-create-which-one-to-call).

## Things easy to miss

- **A table your templates never seed** (an audit/usage log the app writes
  as a side effect): override `delete()`, call `deleteTable(MyTable.X)`
  *last*, then `super.delete()` — see [USERGUIDE.md § A table your templates never seed](../../../USERGUIDE.md#a-table-your-templates-never-seed)
  for why last-called means first-deleted.
- **A column needing a raw SQL expression** (PostGIS point, `CAST`, enum
  coercion): wrap the value in `new SqlExpression("...", params...)` instead
  of a plain literal.
- **A project-specific value type** (a custom date/money type): override
  `setDataColumn` and tag the relevant `TestColumn` constants with
  `targetType()` — don't convert inline at every `setColumn()` call site.
- **Read-back assertions**: add a named method on your builder subclass
  (`currentOrderStatus(Data order)`) using `getJdbcTemplate()`, rather than
  inlining a `SELECT` at each call site.

## Full reference

[USERGUIDE.md](../../USERGUIDE.md) in this repo has the complete walkthrough
with every template pattern, naming (`withName`), the E2E `withDeleteAll`
escape hatch, and vendor-support details. This skill is the condensed
"what do I write in my project" version; the USERGUIDE is the prose version
worth reading once end-to-end.
