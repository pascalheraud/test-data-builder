package ovh.heraud.testdatabuilder.generic;

/**
 * A database vendor {@link TestDataBuilder} knows how to read a generated
 * key back from after an {@code INSERT}. Kept to the same vendors NativSQL
 * supports, since this library's own test suite reuses NativSQL's
 * Testcontainers setup for each of them.
 */
public enum DatabaseVendor {
	/** PostgreSQL — the generated key comes back under the {@code "id"} entry. */
	POSTGRESQL,
	/** MariaDB — the generated key comes back under the {@code "insert_id"} entry. */
	MARIADB,
	/** MySQL — the generated key comes back under the {@code "GENERATED_KEY"} entry. */
	MYSQL,
	/** Oracle — the generated key comes back under the uppercase {@code "ID"} entry (falling back to {@code "id"}). */
	ORACLE
}
