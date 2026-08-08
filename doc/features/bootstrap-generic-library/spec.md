# Bootstrap the TestDataBuilder library (Issue #1)

## Why

Seeding a database for repository or E2E tests through the application's
own entities/ORM/repositories ties test data to the very code path the test
is meant to exercise — a bug or a schema drift in that layer can silently
corrupt what the test believes it inserted, and masks bugs the test should
catch. Seeding through raw SQL instead keeps test data setup independent
of, and safe from, the code under test.

This feature establishes that tool — TestDataBuilder — as its own
standalone, versioned, buildable library, ready for any project to pull in
as a dependency rather than writing it from scratch.

### Language

English is the language for everything in this repository — code, comments,
commit messages, issues, and documentation (README, user guide, changelog,
this spec included) — regardless of who contributes. This repo has no
functional/business-language split the way a French-market product repo
might; it is a generic, potentially publicly-facing library, so there is
only the one language.

## What

### Scope

The library provides a generic model for seeding rows via raw SQL only —
no entity classes, no repositories:

- a **builder** that a project's own subclass extends, adding template
  methods for that project's tables;
- **one row of data to insert**, as a table plus an ordered set of column
  values, where a value can either be a literal or a reference to another
  row (resolved to that row's generated id at insert time, for foreign
  keys);
- a **table abstraction** and a **column abstraction**, both implemented by
  small project-specific enums, so column/table names are typo-checked
  once rather than repeated as raw strings everywhere;
- support for a column that must be inserted as a raw SQL expression
  rather than a plain value (e.g. a geometry column needing a conversion
  function).

On top of that model, the library supports a family of template-writing
patterns for a project's own subclass: a base template with sane defaults,
a shortcut that links to whatever row of a given table was most recently
created, a cluster template that creates several related rows at once, a
partial template that fills in one recurring group of columns on an
already-created row, and a parameterized template for values that vary
often enough to be worth naming as arguments. Every template call also
gets a distinct default per table automatically, so calling the same
template several times in one test never collides.

Lifecycle: a project seeds a batch of rows, then applies them in one call
that clears out anything previously seeded before inserting the new batch,
in an order that respects foreign keys in both directions.

### Database support

The library must work against every database vendor NativSQL supports today
(PostgreSQL, MariaDB, MySQL, Oracle) — a project already using NativSQL for
its application code should be able to use this library for its tests
without introducing a new database vendor into the mix.

Retrieving the id a database just generated for an inserted row differs by
vendor (the SQL/JDBC mechanism to ask for it, and the shape of what comes
back), so that one step is a small, explicit, per-vendor extension point
rather than a hardcoded assumption baked into the insert logic — mirroring
how NativSQL itself already isolates this exact concern into one per-vendor
hook while everything else about building and running the insert stays
vendor-neutral.

### Tests against real databases

Every behavior above is verified against a real running database for each
supported vendor, not mocks or an in-memory substitute — a raw-SQL tool is
only trustworthy if proven against the real SQL dialect it generates for.
Reuse NativSQL's own containerized test databases (one per vendor, already
maintained and published for its test suite) rather than standing up a
second, separate set of containers for the same vendors.

### Deliverables

- **Buildable and versioned.** The library builds standalone and carries a
  first release, `1.0.0`, marking the point where the model above is
  considered complete and stable enough to depend on.
- **Demonstrated, not just described.** The library ships with one small
  example applying the tool to a self-contained, invented domain, touching
  every template pattern above (a base template, a "most recent" shortcut,
  a cluster, a partial template, a parameterized template, a raw-SQL
  column, and a table that needs cleanup without ever being seeded), so
  the example doubles as a working reference alongside the guide. The
  example's tests run against every supported vendor.
- **Documented for a first-time consumer.** A user guide explains how to
  add the dependency and how to model a new project's own tables, columns,
  and templates on top of it — someone should be able to write their
  first builder from the guide alone.
- **README and user guide read as marketing, not just reference.** Both
  the README (repo landing page) and the user guide are written to sell
  the tool to a developer who has never heard of it — lead with the
  problem it solves and why raw SQL beats seeding through an ORM/
  repository layer, before getting into how to use it. Tone: confident
  and concise, not a dry API listing.
- **Change history.** A changelog records the `1.0.0` release as the
  starting point for future versions.
- **Published automatically.** A GitHub Actions workflow builds and
  publishes the library to GitHub's Maven package registry for this repo,
  triggered on release, so a version becomes consumable by other projects
  without a manual publish step.
- **Decisions preserved.** The choices made while shaping this first
  release (versioning scheme, how the example domain was chosen and how
  future examples should extend rather than replace it, the per-vendor
  generated-id extension point and which vendors it covers) are captured
  in a skill in this repo, so they don't have to be re-derived later.

### Non-goals

- Integrating the library into any existing project — this issue delivers
  the library itself, not its adoption elsewhere.

## Acceptance criteria

- The library builds on its own and declares version `1.0.0`.
- The example domain is fully self-contained and its tests pass against a
  real database, exercising every template pattern listed above, for each
  vendor NativSQL supports (PostgreSQL, MariaDB, MySQL, Oracle), using
  NativSQL's own test containers rather than a separately built set.
- Generated-id retrieval is vendor-neutral in the core insert logic, with a
  single per-vendor hook to override — no vendor-specific SQL (e.g.
  `RETURNING`) hardcoded into the generic insert path.
- A changelog documents the `1.0.0` release.
- A user guide lets a newcomer add the dependency and write a first
  table/column/template without reading the library's own source.
- A GitHub Actions workflow publishes the built artifact to GitHub's Maven
  package registry on release.
- A skill in this repo records the decisions behind this first release for
  future reference.
