package ovh.heraud.testdatabuilder.bookstore.tests;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import ovh.heraud.testdatabuilder.bookstore.BookstoreTable;
import ovh.heraud.testdatabuilder.bookstore.BookstoreTestDataBuilder;
import ovh.heraud.testdatabuilder.generic.Data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** {@code delete()}/{@code withDeleteAll()}/{@code apply()} behavior, shared across every vendor. */
public interface DeleteBehavior {

	BookstoreTestDataBuilder builder();

	@Test
	default void deleteRemovesChildRowsBeforeParentRows() {
		// Given a publisher and a book referencing it, both created
		builder().newPublisher();
		builder().newBookForCurrentPublisher();
		builder().create();

		// When deleting
		// book references publisher: deleting publisher first would violate the FK.
		// delete() must delete book (touched after publisher) before publisher.
		assertThatCode(() -> builder().delete()).doesNotThrowAnyException();
		int bookCount = builder().countBooks();
		int publisherCount = builder().countPublishers();

		// Then both tables are empty
		assertThat(bookCount).isZero();
		assertThat(publisherCount).isZero();
	}

	@Test
	default void deleteTableClearsATableNothingSeededWithoutViolatingItsForeignKey() {
		// Given a customer and an order created, plus an order_event row written
		// directly — order_event is written by application code, never through
		// the builder, so insert it directly here as the application under test would.
		builder().newCustomer();
		Data order = builder().newOrderForCurrentCustomer();
		builder().create();
		builder().getJdbcTemplate().update(
				"INSERT INTO order_event (order_id, event_type, occurred_at) VALUES (?, ?, ?)",
				order.getGeneratedId(), "CREATED", Timestamp.valueOf(LocalDateTime.now()));

		// When deleting
		// BookstoreTestDataBuilder.delete() calls deleteTable(ORDER_EVENT) last, so it is
		// removed first — before the order it references.
		assertThatCode(() -> builder().delete()).doesNotThrowAnyException();
		int orderEventCount = builder().countOrderEvents();
		int orderCount = builder().countOrders();
		int customerCount = builder().countCustomers();

		// Then every table, including the unseeded order_event, is empty
		assertThat(orderEventCount).isZero();
		assertThat(orderCount).isZero();
		assertThat(customerCount).isZero();
	}

	@Test
	default void deleteAloneRemovesRowsWithoutInsertingAnythingNew() {
		// Given a publisher already applied
		builder().newPublisher();
		builder().apply();

		// When calling delete() alone, with nothing new registered
		builder().delete();
		int publisherCount = builder().countPublishers();

		// Then the table is empty — unlike apply(), delete() alone never inserts
		assertThat(publisherCount).isZero();
	}

	@Test
	default void withDeleteAllReplacesTheTrackedTablesWithExactlyWhatIsPassed() {
		// Given a publisher, a book, and a customer, all created
		builder().newPublisher();
		builder().newBookForCurrentPublisher();
		builder().newCustomer();
		builder().create();

		// When explicitly restricting cleanup to book/publisher — customer is left
		// alone even though the builder touched it
		builder().withDeleteAll(BookstoreTable.BOOK, BookstoreTable.PUBLISHER);
		builder().delete();
		int bookCount = builder().countBooks();
		int publisherCount = builder().countPublishers();
		int customerCount = builder().countCustomers();

		// Then only book and publisher are cleared, customer still has its row
		assertThat(bookCount).isZero();
		assertThat(publisherCount).isZero();
		assertThat(customerCount).isEqualTo(1);
	}

	@Test
	default void applyClearsThePreviousBatchBeforeInsertingTheNewOne() {
		// Given a first publisher/book pair applied
		builder().newPublisher();
		builder().newBookForCurrentPublisher();
		builder().apply();
		int publisherCountAfterFirstApply = builder().countPublishers();
		int bookCountAfterFirstApply = builder().countBooks();
		assertThat(publisherCountAfterFirstApply).isEqualTo(1);
		assertThat(bookCountAfterFirstApply).isEqualTo(1);

		// When a second "scenario" is seeded and applied, reusing the same builder instance
		builder().newPublisher();
		builder().newBookForCurrentPublisher();
		builder().apply();
		int publisherCountAfterSecondApply = builder().countPublishers();
		int bookCountAfterSecondApply = builder().countBooks();

		// Then only the second scenario's rows remain — apply() cleared the first batch first
		assertThat(publisherCountAfterSecondApply).isEqualTo(1);
		assertThat(bookCountAfterSecondApply).isEqualTo(1);
	}
}
