# Changelog

All notable changes to TestDataBuilder will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-08-09

### Fixed

- `TestDataBuilder.setDataColumn`: a `java.util.Date` value is now converted
  per the column's `TargetTypeEnum` (`DATE` → `java.sql.Date`, `TIMESTAMP` →
  `java.sql.Timestamp`, `STRING` → `Date#toString()`) before being stored.
  Previously the base implementation stored `java.util.Date` values as-is,
  which the PostgreSQL JDBC driver cannot infer a SQL type for
  (`Can't infer the SQL type to use for an instance of java.util.Date`) —
  this conversion had to be reimplemented ad hoc in every project subclass.

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
