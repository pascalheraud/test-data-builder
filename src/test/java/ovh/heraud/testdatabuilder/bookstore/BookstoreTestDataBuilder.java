package ovh.heraud.testdatabuilder.bookstore;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import javax.sql.DataSource;

import ovh.heraud.testdatabuilder.generic.Data;
import ovh.heraud.testdatabuilder.generic.DatabaseVendor;
import ovh.heraud.testdatabuilder.generic.SqlExpression;
import ovh.heraud.testdatabuilder.generic.TestColumn;
import ovh.heraud.testdatabuilder.generic.TestDataBuilder;

/**
 * Example project-specific builder for the bookstore demo domain. Exercises
 * every template pattern documented for {@link TestDataBuilder}: a base
 * template, a "for current" shortcut, a cluster template, a partial
 * template, a parameterized template, an {@link SqlExpression} column, a
 * {@link #deleteTable} override for a table nothing seeds, a
 * {@link #setDataColumn} override converting a project-specific value type
 * ({@link BookstoreDate}) per column {@link TestColumn#targetType()}, and a
 * {@link #getJdbcTemplate()}-backed read-back helper
 * ({@link #currentOrderStatus}) for state outside the {@link Data} mechanism.
 *
 * <p>
 * One instance is built per vendor in this library's own test suite — same
 * class, only the {@link DatabaseVendor} passed to the constructor changes.
 */
public class BookstoreTestDataBuilder extends TestDataBuilder {

	private static final LocalDateTime BASE_ORDER_DATE = LocalDateTime.of(2024, 1, 1, 10, 0);

	public BookstoreTestDataBuilder(DataSource dataSource, DatabaseVendor vendor) {
		super(dataSource, vendor);
	}

	/**
	 * Converts a {@link BookstoreDate} to a {@link Timestamp} for a
	 * {@code TIMESTAMP}-targeted column (see {@link OrderColumn#PLACED_AT}).
	 * Mirrors how a real project converts its own date-builder type — the
	 * generic {@link Data#setColumn} never needs to know {@link BookstoreDate}
	 * exists.
	 */
	@Override
	public Data setDataColumn(Data data, TestColumn column, Object value) {
		if (value instanceof BookstoreDate bookstoreDate && column.targetType() == TargetTypeEnum.TIMESTAMP) {
			return data.setColumn(column, Timestamp.valueOf(bookstoreDate.value()));
		}
		return super.setDataColumn(data, column, value);
	}

	// --- base templates ---

	public Data newPublisher() {
		Data publisher = newData(BookstoreTable.PUBLISHER);
		int index = publisher.getIndex();
		return register(publisher
				.setColumn(PublisherColumn.NAME, "Publisher " + index)
				.setColumn(PublisherColumn.COUNTRY, index % 2 == 0 ? "FR" : "US"));
	}

	public Data newCustomer() {
		Data customer = newData(BookstoreTable.CUSTOMER);
		int index = customer.getIndex();
		return register(customer
				.setColumn(CustomerColumn.EMAIL, "customer" + index + "@example.com")
				.setColumn(CustomerColumn.FULL_NAME, "Customer " + index)
				.setColumn(CustomerColumn.LOYALTY_POINTS, 10 + index)
				.setColumn(CustomerColumn.STATUS, CustomerStatus.values()[index % CustomerStatus.values().length]
						.name())
				// UUID: a technical identifier, not derived from the index — see the skill's
				// "the one exception" rule. Stored as a string so it binds identically across
				// every supported vendor, rather than relying on a native UUID column type.
				.setColumn(CustomerColumn.EXTERNAL_ID, UUID.randomUUID().toString()));
	}

	public Data newWarehouse() {
		Data warehouse = newData(BookstoreTable.WAREHOUSE);
		int index = warehouse.getIndex();
		return register(warehouse
				.setColumn(WarehouseColumn.NAME, "Warehouse " + index)
				.setColumn(WarehouseColumn.CODE, new SqlExpression("UPPER(?)", "wh-" + index))
				.setColumn(WarehouseColumn.LATITUDE, new BigDecimal("45.308556").add(BigDecimal.valueOf(index)))
				.setColumn(WarehouseColumn.LONGITUDE, new BigDecimal("5.885139").add(BigDecimal.valueOf(index))));
	}

	// --- "for current" shortcuts ---

	public Data newBookForCurrentPublisher() {
		Data book = newData(BookstoreTable.BOOK);
		int index = book.getIndex();
		return register(book
				.setColumn(BookColumn.TITLE, "Book " + index)
				.setColumn(BookColumn.ISBN, "978-0-000-" + String.format("%06d", index))
				.setColumn(BookColumn.PRICE, new BigDecimal("19.90").add(BigDecimal.valueOf(index)))
				.setColumn(BookColumn.PUBLISHER_ID, current(BookstoreTable.PUBLISHER)));
	}

	public Data newOrderForCurrentCustomer() {
		Data order = newData(BookstoreTable.ORDERS);
		int index = order.getIndex();
		return register(order
				.setColumn(OrderColumn.CUSTOMER_ID, current(BookstoreTable.CUSTOMER))
				.setColumn(OrderColumn.STATUS, "NEW")
				.setColumn(OrderColumn.PLACED_AT, BASE_ORDER_DATE.plusDays(index)));
	}

	/**
	 * Same as {@link #newOrderForCurrentCustomer()}, but with a caller-given
	 * {@code placedAt} instead of the base template's default — routed through
	 * {@link #setDataColumn} (not {@link Data#setColumn} directly) so the
	 * {@link BookstoreDate}→{@link Timestamp} conversion above actually runs.
	 */
	public Data newOrderForCurrentCustomer(BookstoreDate placedAt) {
		Data order = newOrderForCurrentCustomer();
		return setDataColumn(order, OrderColumn.PLACED_AT, placedAt);
	}

	public Data newOrderItemForCurrentOrder() {
		return newOrderItemForOrder(current(BookstoreTable.ORDERS));
	}

	/**
	 * Same as {@link #newOrderItemForCurrentOrder()}, but for a specific order
	 * rather than the most recently created one — needed to add a second item
	 * to an order created earlier, once other templates (e.g. a second
	 * customer/order pair) have made it no longer "current".
	 */
	public Data newOrderItemForOrder(Data order) {
		Data item = newData(BookstoreTable.ORDER_ITEM);
		int index = item.getIndex();
		return register(item
				.setColumn(OrderItemColumn.ORDER_ID, order)
				.setColumn(OrderItemColumn.BOOK_ID, current(BookstoreTable.BOOK))
				.setColumn(OrderItemColumn.QUANTITY, 1 + index % 5)
				.setColumn(OrderItemColumn.UNIT_PRICE, new BigDecimal("19.90")));
	}

	// --- cluster template ---

	public OrderWithItem newOrderWithItem() {
		Data order = newOrderForCurrentCustomer();
		Data item = newOrderItemForCurrentOrder();
		return new OrderWithItem(order, item);
	}

	// --- partial template ---

	public Data setCustomerAsLoyal(Data customer) {
		return customer
				.setColumn(CustomerColumn.LOYALTY_POINTS, 1000)
				.setColumn(CustomerColumn.STATUS, CustomerStatus.GOLD.name());
	}

	// --- parameterized template ---

	/**
	 * {@code price} is a plain {@code String} (e.g. {@code "39.90"}) rather
	 * than a {@link BigDecimal} — the call site reads as a literal, and this
	 * one template is where the {@code BigDecimal} construction happens
	 * instead of being repeated at every call site.
	 */
	public Data newBook(String title, String price) {
		return newBookForCurrentPublisher()
				.setColumn(BookColumn.TITLE, title)
				.setColumn(BookColumn.PRICE, new BigDecimal(price));
	}

	// --- order_event: written by application code, never seeded ---

	@Override
	public void delete() {
		deleteTable(BookstoreTable.ORDER_EVENT);
		super.delete();
	}

	// --- read-back helpers, outside the Data template mechanism ---

	/**
	 * {@code orders.status}, re-read after application code under test has
	 * updated it — {@code order} was seeded by this builder, but its status
	 * may have since changed as a side effect of the code under test, so the
	 * in-memory {@link Data} no longer reflects it. Uses {@link #getJdbcTemplate()}
	 * directly, the same way a real project reads back mutated state.
	 */
	public String currentOrderStatus(Data order) {
		return getJdbcTemplate().queryForObject(
				"SELECT status FROM orders WHERE id = ?", String.class, order.getGeneratedId());
	}

	/** {@code book.publisher_id}, re-read from the database rather than resolved off the in-memory {@link Data}. */
	public Long currentBookPublisherId(Data book) {
		return getJdbcTemplate().queryForObject(
				"SELECT publisher_id FROM book WHERE id = ?", Long.class, book.getGeneratedId());
	}

	/** {@code order_item.order_id}, re-read from the database rather than resolved off the in-memory {@link Data}. */
	public Long currentOrderItemOrderId(Data orderItem) {
		return getJdbcTemplate().queryForObject(
				"SELECT order_id FROM order_item WHERE id = ?", Long.class, orderItem.getGeneratedId());
	}

	/** Number of {@code order_item} rows for {@code order}. */
	public int countOrderItems(Data order) {
		return getJdbcTemplate().queryForObject(
				"SELECT COUNT(*) FROM order_item WHERE order_id = ?", Integer.class, order.getGeneratedId());
	}

	/** {@code customer.status}, re-read after the app under test has updated it. */
	public String currentCustomerStatus(Data customer) {
		return getJdbcTemplate().queryForObject(
				"SELECT status FROM customer WHERE id = ?", String.class, customer.getGeneratedId());
	}

	/** {@code warehouse.code}, re-read from the database — verifies what an {@link SqlExpression} column actually stored. */
	public String currentWarehouseCode(Data warehouse) {
		return getJdbcTemplate().queryForObject(
				"SELECT code FROM warehouse WHERE id = ?", String.class, warehouse.getGeneratedId());
	}

	/** {@code orders.placed_at}, re-read from the database — verifies what {@link #setDataColumn} actually stored. */
	public LocalDateTime currentOrderPlacedAt(Data order) {
		return getJdbcTemplate().queryForObject(
				"SELECT placed_at FROM orders WHERE id = ?", LocalDateTime.class, order.getGeneratedId());
	}

	/** Number of {@code publisher} rows — e.g. to assert {@link #delete()}/{@link #apply()} actually cleared it. */
	public int countPublishers() {
		return countRows(BookstoreTable.PUBLISHER);
	}

	/** Number of {@code book} rows. */
	public int countBooks() {
		return countRows(BookstoreTable.BOOK);
	}

	/** Number of {@code customer} rows. */
	public int countCustomers() {
		return countRows(BookstoreTable.CUSTOMER);
	}

	/** Number of {@code orders} rows. */
	public int countOrders() {
		return countRows(BookstoreTable.ORDERS);
	}

	/** Number of {@code warehouse} rows. */
	public int countWarehouses() {
		return countRows(BookstoreTable.WAREHOUSE);
	}

	/** Number of {@code order_event} rows — {@code order_event} is never seeded, only ever written directly (see {@link #delete()}). */
	public int countOrderEvents() {
		return countRows(BookstoreTable.ORDER_EVENT);
	}
}
