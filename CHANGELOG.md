# Changelog

All notable changes to TestDataBuilder will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-08-08

### Added

- `TestDataBuilder` generic core: `TestDataBuilder`, `Data`, `TestTable`,
  `TestColumn`, `SqlExpression`, `DatabaseVendor` — seeds rows via raw SQL
  only, no entity classes, no repositories.
- Template patterns: base template, "for current" shortcut, cluster
  template, partial template, parameterized template, per-table
  index-derived distinct defaults.
- Vendor-neutral generated-id retrieval (`Statement.RETURN_GENERATED_KEYS`
  instead of a hardcoded `RETURNING` clause), supporting the same database
  vendors as [NativSQL](https://github.com/pascalheraud/nativsql):
  PostgreSQL, MariaDB, MySQL, Oracle — selected via the `DatabaseVendor`
  passed to the constructor, no subclassing required.
- `deleteTable`/`withDeleteAll` for cleanup of tables outside the normal
  seed-and-delete lifecycle.
- Bookstore example project (test sources), demonstrating every template
  pattern and tested against all four supported vendors using NativSQL's
  own Testcontainers-based test fixtures.
- Gradle build, published to GitHub Packages.
