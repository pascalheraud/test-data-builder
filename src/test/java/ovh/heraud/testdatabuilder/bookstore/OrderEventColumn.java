package ovh.heraud.testdatabuilder.bookstore;

import ovh.heraud.testdatabuilder.generic.TestColumn;

/**
 * Columns of {@code order_event}, a table written by application code as a
 * side effect of processing an order — never seeded by a template. Used to
 * demonstrate {@link ovh.heraud.testdatabuilder.generic.TestDataBuilder#deleteTable}.
 */
public enum OrderEventColumn implements TestColumn {
	ID("id"), ORDER_ID("order_id"), EVENT_TYPE("event_type"), OCCURRED_AT("occurred_at");

	private final String sqlName;

	OrderEventColumn(String sqlName) {
		this.sqlName = sqlName;
	}

	@Override
	public String getSqlName() {
		return sqlName;
	}
}
