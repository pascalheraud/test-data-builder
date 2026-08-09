package ovh.heraud.testdatabuilder.bookstore.tests;

import org.junit.jupiter.api.Test;

import ovh.heraud.testdatabuilder.bookstore.BookstoreTestDataBuilder;
import ovh.heraud.testdatabuilder.generic.Data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Per-table index/distinct-defaults behavior, shared across every vendor. */
public interface IndexBehavior {

	BookstoreTestDataBuilder builder();

	@Test
	default void twoCallsToTheSameBaseTemplateProduceDistinctIndexDerivedDefaults() {
		// Given a publisher and two books created for it via the same base template
		Data publisher = builder().newPublisher();
		Data first = builder().newBookForCurrentPublisher();
		Data second = builder().newBookForCurrentPublisher();

		// Then each call got a distinct, table-scoped index
		String firstIsbn = first.getColumn("isbn");
		String secondIsbn = second.getColumn("isbn");
		assertThat(firstIsbn).isNotEqualTo(secondIsbn);
		assertThat(publisher.getIndex()).isZero();
		assertThat(first.getIndex()).isZero();
		assertThat(second.getIndex()).isEqualTo(1);

		// When applying them
		// Then there is no unique-constraint violation on the isbn column
		assertThatCode(() -> builder().apply()).doesNotThrowAnyException();
	}

	@Test
	default void indexIsScopedPerTableIndependently() {
		// Given two publishers already created (index 0 and 1)
		builder().newPublisher();
		builder().newPublisher();

		// When creating the first warehouse
		Data warehouse = builder().newWarehouse();

		// Then its index starts back at 0 — indexing is per table, not global
		assertThat(warehouse.getIndex()).isZero();
	}

	@Test
	default void repeatedCustomerAndWarehouseTemplatesNeverCollideOnUniqueColumns() {
		// Given several customers and warehouses created via the same base templates
		builder().newCustomer();
		builder().newCustomer();
		builder().newCustomer();
		builder().newWarehouse();
		builder().newWarehouse();

		// When applying them
		// Then there is no unique-constraint violation on any of their unique columns
		assertThatCode(() -> builder().apply()).doesNotThrowAnyException();
	}
}
