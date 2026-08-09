package ovh.heraud.testdatabuilder.bookstore.mysql;

import org.junit.jupiter.api.BeforeEach;

import ovh.heraud.nativsql.repository.mysql.MySQLBaseRepositoryTest;
import ovh.heraud.testdatabuilder.bookstore.BookstoreTestDataBuilder;
import ovh.heraud.testdatabuilder.bookstore.tests.CreateBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.DeleteBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.IndexBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.RegistrationBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.TemplateBehavior;
import ovh.heraud.testdatabuilder.generic.DatabaseVendor;

/**
 * Runs every {@code *Behavior} interface's tests against MySQL — reuses
 * NativSQL's own Testcontainers base class instead of standing up a second
 * container setup.
 */
class MySqlBookstoreTest extends MySQLBaseRepositoryTest
		implements RegistrationBehavior, IndexBehavior, CreateBehavior, DeleteBehavior, TemplateBehavior {

	private BookstoreTestDataBuilder builder;

	@Override
	protected String getScriptPath() {
		return "bookstore-schema-mysql.sql";
	}

	@BeforeEach
	void createBuilder() {
		builder = new BookstoreTestDataBuilder(getDataSource(), DatabaseVendor.MYSQL);
	}

	@Override
	public BookstoreTestDataBuilder builder() {
		return builder;
	}
}
