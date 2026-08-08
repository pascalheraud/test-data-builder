package ovh.heraud.testdatabuilder.bookstore.mariadb;

import org.junit.jupiter.api.BeforeEach;

import ovh.heraud.nativsql.repository.mariadb.MariaDBBaseRepositoryTest;
import ovh.heraud.testdatabuilder.bookstore.BookstoreTestDataBuilder;
import ovh.heraud.testdatabuilder.bookstore.tests.CreateBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.DeleteBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.IndexBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.RegistrationBehavior;
import ovh.heraud.testdatabuilder.bookstore.tests.TemplateBehavior;
import ovh.heraud.testdatabuilder.generic.DatabaseVendor;

/**
 * Runs every {@code *Behavior} interface's tests against MariaDB — reuses
 * NativSQL's own Testcontainers base class instead of standing up a second
 * container setup.
 */
class MariaDbBookstoreTest extends MariaDBBaseRepositoryTest
		implements RegistrationBehavior, IndexBehavior, CreateBehavior, DeleteBehavior, TemplateBehavior {

	private BookstoreTestDataBuilder builder;

	@Override
	protected String getScriptPath() {
		return "bookstore-schema-mariadb.sql";
	}

	@BeforeEach
	void createBuilder() {
		builder = new BookstoreTestDataBuilder(getDataSource(), DatabaseVendor.MARIADB);
	}

	@Override
	public BookstoreTestDataBuilder builder() {
		return builder;
	}
}
