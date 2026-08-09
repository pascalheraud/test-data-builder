package ovh.heraud.testdatabuilder.bookstore;

import ovh.heraud.testdatabuilder.generic.TestColumn;
import ovh.heraud.testdatabuilder.generic.TestTable;

/**
 * Tables of the bookstore example domain — a self-contained demonstration
 * schema, unrelated to any consumer's own domain, exercising every template
 * pattern documented for {@link ovh.heraud.testdatabuilder.generic.TestDataBuilder}.
 */
public enum BookstoreTable implements TestTable {

	PUBLISHER("publisher", PublisherColumn.values()),
	BOOK("book", BookColumn.values()),
	CUSTOMER("customer", CustomerColumn.values()),
	ORDERS("orders", OrderColumn.values()),
	ORDER_ITEM("order_item", OrderItemColumn.values()),
	WAREHOUSE("warehouse", WarehouseColumn.values()),
	ORDER_EVENT("order_event", OrderEventColumn.values());

	private final String sqlName;
	private final TestColumn[] columns;

	BookstoreTable(String sqlName, TestColumn[] columns) {
		this.sqlName = sqlName;
		this.columns = columns;
	}

	@Override
	public String getSqlName() {
		return sqlName;
	}

	@Override
	public TestColumn[] getColumns() {
		return columns;
	}
}
