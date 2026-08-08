package ovh.heraud.testdatabuilder.bookstore.tests;

import org.junit.jupiter.api.Test;

import ovh.heraud.testdatabuilder.bookstore.BookstoreTestDataBuilder;
import ovh.heraud.testdatabuilder.generic.Data;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code create()} behavior, shared across every vendor. */
public interface CreateBehavior {

	BookstoreTestDataBuilder builder();

	@Test
	default void createInsertsEveryRegisteredRowAndAssignsAGeneratedId() {
		// Given a publisher and a book registered but not yet inserted
		Data publisher = builder().newPublisher();
		Data book = builder().newBookForCurrentPublisher();

		// When creating them
		builder().create();

		// Then both rows are inserted and carry a generated id
		assertThat(publisher.getGeneratedId()).isNotNull();
		assertThat(book.getGeneratedId()).isNotNull();
		assertThat(publisher.isAdded()).isTrue();
		assertThat(book.isAdded()).isTrue();
	}

	@Test
	default void aReferencedDataIsResolvedToItsGeneratedIdOnlyAfterItIsItselfInserted() {
		// Given a publisher and a book referencing it
		Data publisher = builder().newPublisher();
		Data book = builder().newBookForCurrentPublisher();

		// When creating them
		builder().create();
		Long publisherIdOnBook = builder().currentBookPublisherId(book);

		// Then the book's foreign key resolved to the publisher's generated id
		assertThat(publisherIdOnBook).isEqualTo(publisher.getGeneratedId());
	}

	@Test
	default void createCalledTwiceOnlyInsertsNewlyRegisteredData() {
		// Given a first publisher already created
		Data firstPublisher = builder().newPublisher();
		builder().create();
		Long firstGeneratedId = firstPublisher.getGeneratedId();

		// When registering a second publisher and calling create() again
		Data secondPublisher = builder().newPublisher();
		builder().create();
		int publisherCount = builder().countPublishers();

		// Then the first publisher is untouched and only the second one was inserted
		assertThat(firstPublisher.getGeneratedId()).isEqualTo(firstGeneratedId);
		assertThat(secondPublisher.getGeneratedId()).isNotNull().isNotEqualTo(firstGeneratedId);
		assertThat(publisherCount).isEqualTo(2);
	}

	@Test
	default void createAloneNeverDeletesEvenAfterAnEarlierApply() {
		// Given a first publisher already applied (seeded and inserted)
		builder().newPublisher();
		builder().apply();

		// When registering a second publisher and calling create() alone — not apply()
		builder().newPublisher();
		builder().create();
		int publisherCount = builder().countPublishers();

		// Then both publishers are present — unlike apply(), create() alone never deletes
		assertThat(publisherCount).isEqualTo(2);
	}

	@Test
	default void sqlExpressionColumnIsInlinedWithItsOwnBindParams() {
		// Given a warehouse whose code column is set via a SqlExpression (UPPER(?))
		Data warehouse = builder().newWarehouse();

		// When creating it
		builder().create();
		String code = builder().currentWarehouseCode(warehouse);

		// Then the expression ran in the database, not in Java
		assertThat(code).isEqualTo("WH-0");
	}
}
