---
name: test-data-builder-library
description: Decisions behind this repo's TestDataBuilder library — versioning, the bookstore example domain, database vendor support, and API conventions specific to this codebase.
---

# TestDataBuilder library (this repo)

For the model itself (`TestDataBuilder`, `Data`, `TestTable`, `TestColumn`,
`SqlExpression`, template patterns, naming, lifecycle), see
[USERGUIDE.md](../../../USERGUIDE.md) or `test-data-builder-usage` — this
skill only captures decisions specific to *this repo's implementation*
(versioning, the bookstore example, vendor support, API conventions).

## Language

English only — code, comments, commit messages, issues, and every doc file
(README, USERGUIDE, CHANGELOG, this skill included). This repo has no
functional/business-language split; it's a generic, potentially
publicly-facing library.

## Test style: Given/When/Then

Every test method follows [[backend/java/test/playwright]]'s Given/When/Then
structure — a comment per section, even for one-liners. Applies here even
though these aren't E2E tests; the convention is about readability, not
about the test level.

**When the "When" step performs an action that returns a value, capture it
in a named local variable — even when it's only used once — instead of
calling it inline inside the "Then" assertion.**

```java
// When looking it up by name and table
Data found = builder.getData("root", BookstoreTable.PUBLISHER);

// Then it is retrievable
assertThat(found).isSameAs(publisher);
```

not

```java
// When looking it up by name and table
// Then it is retrievable
assertThat(builder.getData("root", BookstoreTable.PUBLISHER)).isSameAs(publisher);
```

The point is for the "When" step to be materialized as its own visible
statement — a reader scanning the method should see the action happen, not
have to find it buried inside an assertion argument. This doesn't apply to
`assertThatCode(() -> ...).doesNotThrowAnyException()`/`assertThatThrownBy`
style assertions about a void action itself (there's no return value to
name — the lambda *is* the "When").

## Versioning

Starts at `1.0.0` — this is a first, complete extraction of the generic
model, not an evolving pre-release, so there's no pre-1.0 history to
preserve.

## The bookstore is the one canonical example domain

`src/test/java/.../bookstore/` is deliberately the only example domain in
this repo. Any future addition that needs a new template pattern extends
the bookstore schema (a new table/column, a new template method) rather
than introducing a second, unrelated demo domain — two demo domains would
double the vendor-schema maintenance burden (four SQL scripts each) for no
benefit.

## Database vendor support and the `DatabaseVendor` extension point

Supported vendors are exactly [NativSQL](https://github.com/pascalheraud/nativsql)'s:
PostgreSQL, MariaDB, MySQL, Oracle. This isn't arbitrary — this repo's own
test suite reuses NativSQL's Testcontainers-based `test-fixtures` artifacts
(`nativsql-<vendor>-test-fixtures`) instead of maintaining a second set of
containers, so the vendor list can't outgrow NativSQL's without first
adding that container support upstream.

`TestDataBuilder`'s only vendor-specific behavior is reading back a
generated id after `INSERT` (`Statement.RETURN_GENERATED_KEYS` — no
`RETURNING` clause, works unmodified across all four vendors as an insert
mechanism, but the JDBC driver's key-map shape differs by vendor). This is
handled by a `DatabaseVendor` enum passed to the constructor, resolved
internally via `resolveGeneratedKey` — **not** by per-vendor subclassing or
method overrides. A `BookstoreTestDataBuilder` instance for MariaDB and one
for PostgreSQL are the exact same Java class; only the constructor argument
differs. This was a deliberate correction during implementation — an
earlier version had `resolveGeneratedKey` as a `protected` method each
vendor's subclass overrode, which meant one Java subclass per vendor for no
real reason (the entire vendor-specific logic is a 1-line map lookup with a
different key name). Prefer the enum-driven design for any future
per-vendor behavior this library needs — only fall back to subclassing if a
vendor needs something too large for a `switch` (it hasn't happened yet).

Per-vendor key names, mirroring NativSQL's own `DatabaseDialect.getGeneratedKey`
exactly (don't re-derive these from scratch if a new vendor is ever added —
check NativSQL's per-vendor dialect first):

| Vendor | Key entry |
|---|---|
| PostgreSQL | `id` |
| Oracle | `ID` (uppercase), falls back to `id` |
| MySQL | `GENERATED_KEY` |
| MariaDB | `insert_id` |

## Known upstream gap: NativSQL's `test-fixtures` POMs have no declared dependencies

`nativsql-<vendor>-test-fixtures` artifacts are published via a
`MavenPublication` built directly from `tasks.testFixturesJar`, without
Gradle module metadata for that publication — the resulting POM has no
`<dependencies>` block at all. Consuming them from outside NativSQL's own
multi-project Gradle build (i.e., from this repo) does **not** pull in
`nativsql-core`, `nativsql-core-test-fixtures`, `spring-boot-testcontainers`,
or the Testcontainers modules transitively — every one of them has to be
declared explicitly in this repo's `build.gradle` `testImplementation`
block instead, version-aligned with the `spring-boot-dependencies` BOM
version actually imported by the published `nativsql-*-test-fixtures` POM
for the pinned `nativsqlVersion` (check that POM's `<dependencyManagement>`
before bumping either version — it has drifted between NativSQL releases,
e.g. `4.0.4` as of `2.11.0`) and NativSQL's own `testcontainers-bom` pin.
If NativSQL's publish-conventions gain proper Gradle module metadata for
`test-fixtures` publications, this repo's explicit dependency list becomes
redundant and can be trimmed back to just the `nativsql-*-test-fixtures`
coordinates — worth revisiting if NativSQL's `CHANGELOG.md` ever mentions
it.

## `nativsqlVersion` must be a version actually published to GitHub Packages

NativSQL's local working tree (`gradle.properties`) is routinely ahead of
what's actually been released — `./gradlew publishToMavenLocal` in that
repo happily publishes an unreleased version to the local Maven cache,
which then resolves fine here *only* because it's sitting in `~/.m2`, not
because GitHub Packages actually has it. Don't trust a successful local
build as confirmation that `nativsqlVersion` is pinned to something CI (or
any other machine) can resolve. Check what's actually published before
bumping:

```
curl -s -u "$USER:$TOKEN" \
  https://maven.pkg.github.com/pascalheraud/nativsql/ovh/heraud/nativsql-postgres-test-fixtures/maven-metadata.xml
```

`mavenLocal()` is deliberately **not** in this repo's `repositories {}` —
having it would let exactly this mistake pass silently on a machine that
happens to have a newer, unreleased NativSQL version published locally.

## Publishing and GitHub Packages authentication

GitHub Packages only (`.github/workflows/publish.yml`, triggered on
`release: published`), using the built-in `GITHUB_TOKEN` — no separate PAT.
Maven Central is explicitly out of scope.

Reading from GitHub Packages requires authentication even for a public
package — resolving `nativsql-*-test-fixtures` (needed just to build this
repo) or consuming this library's own published artifact both fail with
`Username must not be null!` without it. CI gets this for free via the
built-in `GITHUB_TOKEN`; a local build needs `gpr.user`/`gpr.key` in the
user's global `~/.gradle/gradle.properties` (never this repo's own
`gradle.properties` — that file's committed and version-only) or the
`GITHUB_ACTOR`/`GITHUB_TOKEN` environment variables.

## API conventions for template builders in this repo

- **Read-back helper methods, not inline SQL in tests.** A test asserting
  on state after `create()`/`apply()` calls a named method on the builder
  (`currentBookPublisherId(Data book)`, `countRows(BookstoreTable table)`)
  rather than building a `SELECT` inline — mirrors a real project's
  `AuxiliairesTestDataBuilder`-style `lastXxx`/`currentXxx`/`countXxx`
  helpers. Add a new one whenever a test needs to read back a column no
  existing helper covers.
- **Don't put "simulate the application" code on the builder.** A few
  tests need to insert/update a row the way the *application under test*
  would (e.g. writing to `order_event`, mutating `orders.status`) — that's
  not seeding, so it stays as plain SQL directly in the test body, with a
  comment saying it simulates the app, rather than becoming a builder
  method. The builder's API surface is for seeding and read-back, not for
  standing in for application code.
- **Parameterized templates take primitives, not the type they'll become.**
  `newBook(String title, String price)` takes `price` as a plain `String`
  and constructs the `BigDecimal` once, inside the template — the call site
  reads as a literal (`newBook("...", "39.90")`) instead of making every
  caller wrap `new BigDecimal(...)` themselves.
