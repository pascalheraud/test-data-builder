package ovh.heraud.testdatabuilder.bookstore.tests;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ovh.heraud.testdatabuilder.bookstore.BookstoreDate;
import ovh.heraud.testdatabuilder.bookstore.BookstoreTestDataBuilder;
import ovh.heraud.testdatabuilder.bookstore.CustomerStatus;
import ovh.heraud.testdatabuilder.bookstore.OrderWithItem;
import ovh.heraud.testdatabuilder.generic.Data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Exercises the whole {@code BookstoreTestDataBuilder} end to end — doubling
 * as the "does every documented template pattern actually work" check.
 * Shared across every vendor.
 */
public interface TemplateBehavior {

	BookstoreTestDataBuilder builder();

	@Test
	default void baseTemplatesInsertWithDistinctIndexDerivedDefaults() {
		// Given a publisher, a customer and a warehouse created via their base templates
		Data publisher = builder().newPublisher();
		Data customer = builder().newCustomer();
		Data warehouse = builder().newWarehouse();

		// When creating them
		builder().create();
		String email = customer.getColumn("email");
		String warehouseName = warehouse.getColumn("name");

		// Then each got the expected, distinct, index-derived defaults
		assertThat(publisher.getGeneratedId()).isNotNull();
		assertThat(email).isEqualTo("customer0@example.com");
		assertThat(warehouseName).isEqualTo("Warehouse 0");
	}

	@Test
	default void forCurrentShortcutLinksToTheLastCreatedRowOfThatTable() {
		// Given two publishers, then a book created "for current"
		builder().newPublisher();
		Data secondPublisher = builder().newPublisher();
		Data book = builder().newBookForCurrentPublisher();

		// When creating them
		builder().create();
		Long publisherIdOnBook = builder().currentBookPublisherId(book);

		// Then the book links to the most recently created publisher, not the first one
		assertThat(publisherIdOnBook).isEqualTo(secondPublisher.getGeneratedId());
	}

	@Test
	default void forOrderVariantAddsASecondItemToAnOrderThatIsNoLongerCurrent() {
		// Given a first order with an item, then a second customer/order that
		// makes the first order no longer "current"
		builder().newCustomer();
		builder().newPublisher();
		builder().newBookForCurrentPublisher();
		OrderWithItem firstOrder = builder().newOrderWithItem();
		builder().newCustomer();
		builder().newOrderWithItem();

		// When adding a second item explicitly to the first order
		Data secondItemOnFirstOrder = builder().newOrderItemForOrder(firstOrder.order());
		builder().create();
		Long orderIdOnSecondItem = builder().currentOrderItemOrderId(secondItemOnFirstOrder);
		int itemCountForFirstOrder = builder().countOrderItems(firstOrder.order());

		// Then the new item is linked to the first order, which now has two items
		assertThat(orderIdOnSecondItem).isEqualTo(firstOrder.order().getGeneratedId());
		assertThat(itemCountForFirstOrder).isEqualTo(2);
	}

	@Test
	default void clusterTemplateReturnsEveryDataItCreatedWithResolvableForeignKeys() {
		// Given a customer, a publisher and a book, then an order-with-item cluster
		builder().newCustomer();
		builder().newPublisher();
		builder().newBookForCurrentPublisher();
		OrderWithItem orderWithItem = builder().newOrderWithItem();

		// When creating them
		builder().create();
		Long orderIdOnItem = builder().currentOrderItemOrderId(orderWithItem.item());

		// Then both the order and the item got a generated id, and the item resolves to that order
		assertThat(orderWithItem.order().getGeneratedId()).isNotNull();
		assertThat(orderWithItem.item().getGeneratedId()).isNotNull();
		assertThat(orderIdOnItem).isEqualTo(orderWithItem.order().getGeneratedId());
	}

	@Test
	default void partialTemplateMutatesAnAlreadyCreatedDataInPlace() {
		// Given a customer, made loyal via the partial template
		Data customer = builder().newCustomer();
		builder().setCustomerAsLoyal(customer);

		// When creating it
		builder().create();
		String status = builder().currentCustomerStatus(customer);

		// Then its status is GOLD
		assertThat(status).isEqualTo(CustomerStatus.GOLD.name());
	}

	@Test
	default void parameterizedTemplateOverridesOnlyTheGivenColumns() {
		// Given a publisher, then a book created via the parameterized template
		builder().newPublisher();
		Data book = builder().newBook("The Pragmatic Programmer", "39.90");

		// When creating it
		builder().create();
		String title = book.getColumn("title");
		BigDecimal price = book.getColumn("price");
		String isbn = book.getColumn("isbn");

		// Then the given title/price were used, and the rest kept its base-template default
		assertThat(title).isEqualTo("The Pragmatic Programmer");
		assertThat(price).isEqualTo(new BigDecimal("39.90"));
		assertThat(isbn).isNotNull();
	}

	@Test
	default void setDataColumnConvertsAProjectSpecificValueTypePerColumnTargetType() {
		// Given a customer, then an order placed at a caller-given BookstoreDate
		builder().newCustomer();
		Data order = builder().newOrderForCurrentCustomer(new BookstoreDate(LocalDateTime.of(2030, 6, 15, 8, 30)));

		// When creating it
		builder().create();
		LocalDateTime placedAt = builder().currentOrderPlacedAt(order);

		// Then setDataColumn converted the BookstoreDate to a Timestamp and stored it
		assertThat(placedAt).isEqualTo(LocalDateTime.of(2030, 6, 15, 8, 30));
	}

	@Test
	default void externalIdIsARandomUuidNotDerivedFromTheIndex() {
		// Given two customers created via the base template
		Data first = builder().newCustomer();
		Data second = builder().newCustomer();
		String firstExternalId = first.getColumn("external_id");
		String secondExternalId = second.getColumn("external_id");

		// Then each got its own random, valid UUID — not one derived from the index
		assertThat(firstExternalId).isNotNull().isNotEqualTo(secondExternalId);
		assertThatCode(() -> UUID.fromString(firstExternalId)).doesNotThrowAnyException();
	}

	@Test
	default void currentOrderStatusReadsBackStateMutatedOutsideTheBuilder() {
		// Given a customer and an order, created
		builder().newCustomer();
		Data order = builder().newOrderForCurrentCustomer();
		builder().create();

		// When the application under test changes the order's status —
		// updated directly here, not through the builder, on purpose
		builder().getJdbcTemplate().update("UPDATE orders SET status = ? WHERE id = ?", "SHIPPED", order.getGeneratedId());
		String status = builder().currentOrderStatus(order);

		// Then the read-back helper reflects the mutated state
		assertThat(status).isEqualTo("SHIPPED");
	}

	@Test
	default void fullScenarioAppliesTwiceWithoutLeftoverRowsOrForeignKeyErrors() {
		// Given a full first scenario (publisher, book, customer, order-with-item,
		// warehouse) applied, plus an order_event row written directly — order_event
		// is written by application code, never through the builder, so insert it
		// directly here as the application under test would
		builder().newPublisher();
		builder().newBookForCurrentPublisher();
		builder().newCustomer();
		OrderWithItem orderWithItem = builder().newOrderWithItem();
		builder().newWarehouse();
		builder().apply();
		builder().getJdbcTemplate().update(
				"INSERT INTO order_event (order_id, event_type, occurred_at) VALUES (?, ?, ?)",
				orderWithItem.order().getGeneratedId(), "CREATED", Timestamp.valueOf(LocalDateTime.now()));

		// When a second scenario is seeded under a different name and applied,
		// reusing the same builder instance
		builder().withName("second-scenario");
		builder().newPublisher();
		builder().newBookForCurrentPublisher();
		builder().newCustomer();
		builder().newOrderWithItem();
		builder().newWarehouse();
		assertThatCode(() -> builder().apply()).doesNotThrowAnyException();

		// Then the first scenario's rows — including the unseeded order_event —
		// are gone, leaving only the second scenario's
		int orderEventCount = builder().countOrderEvents();
		int orderCount = builder().countOrders();
		int publisherCount = builder().countPublishers();
		assertThat(orderEventCount).isZero();
		assertThat(orderCount).isEqualTo(1);
		assertThat(publisherCount).isEqualTo(1);
	}
}
