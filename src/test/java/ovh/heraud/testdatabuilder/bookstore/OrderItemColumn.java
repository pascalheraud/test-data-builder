package ovh.heraud.testdatabuilder.bookstore;

import ovh.heraud.testdatabuilder.generic.TestColumn;

public enum OrderItemColumn implements TestColumn {
	ID("id"), ORDER_ID("order_id"), BOOK_ID("book_id"), QUANTITY("quantity"), UNIT_PRICE("unit_price");

	private final String sqlName;

	OrderItemColumn(String sqlName) {
		this.sqlName = sqlName;
	}

	@Override
	public String getSqlName() {
		return sqlName;
	}
}
