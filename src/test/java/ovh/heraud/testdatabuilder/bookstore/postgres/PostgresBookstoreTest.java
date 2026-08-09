package ovh.heraud.testdatabuilder.bookstore.postgres;

import org.junit.jupiter.api.BeforeEach;

import ovh.heraud.nativsql.repository.postgres.PostgresBaseRepositoryTest;
import ovh.heraud.testdatabuilder.bookstore.BookstoreTestDataBuilder;
import ovh.heraud.testdatabuilder.bookstore.tests.CreateBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.DeleteBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.IndexBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.RegistrationBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.TemplateBehavior;
import ovh.heraud.testdatabuilder.generic.DatabaseVendor;

/**
 * Runs every {@code *Behavior} interface's tests against PostgreSQL — reuses
 * NativSQL's own Testcontainers base class (container caching, schema
 * loading, per-test transaction rollback) instead of standing up a second
 * container setup.
 */
class PostgresBookstoreTest extends PostgresBaseRepositoryTest
		implements RegistrationBehavior, IndexBehavior, CreateBehavior, DeleteBehavior, TemplateBehavior {

	private BookstoreTestDataBuilder builder;

	@Override
	protected String getScriptPath() {
		return "bookstore-schema-postgres.sql";
	}

	@BeforeEach
	void createBuilder() {
		builder = new BookstoreTestDataBuilder(getDataSource(), DatabaseVendor.POSTGRESQL);
	}

	@Override
	public BookstoreTestDataBuilder builder() {
		return builder;
	}
}
