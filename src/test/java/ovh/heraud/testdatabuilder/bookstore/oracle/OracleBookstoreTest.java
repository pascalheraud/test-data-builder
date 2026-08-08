package ovh.heraud.testdatabuilder.bookstore.oracle;

import org.junit.jupiter.api.BeforeEach;

import ovh.heraud.nativsql.repository.oracle.OracleBaseRepositoryTest;
import ovh.heraud.testdatabuilder.bookstore.BookstoreTestDataBuilder;
import ovh.heraud.testdatabuilder.bookstore.tests.CreateBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.DeleteBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.IndexBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.RegistrationBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.TemplateBehavior;
import ovh.heraud.testdatabuilder.generic.DatabaseVendor;

/**
 * Runs every {@code *Behavior} interface's tests against Oracle — reuses
 * NativSQL's own Testcontainers base class instead of standing up a second
 * container setup.
 */
class OracleBookstoreTest extends OracleBaseRepositoryTest
		implements RegistrationBehavior, IndexBehavior, CreateBehavior, DeleteBehavior, TemplateBehavior {

	private BookstoreTestDataBuilder builder;

	@Override
	protected String getScriptPath() {
		return "bookstore-schema-oracle.sql";
	}

	@BeforeEach
	void createBuilder() {
		builder = new BookstoreTestDataBuilder(getDataSource(), DatabaseVendor.ORACLE);
	}

	@Override
	public BookstoreTestDataBuilder builder() {
		return builder;
	}
}
