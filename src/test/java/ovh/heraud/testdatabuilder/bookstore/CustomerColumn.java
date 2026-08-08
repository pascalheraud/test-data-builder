package ovh.heraud.testdatabuilder.bookstore;

import ovh.heraud.testdatabuilder.generic.TestColumn;

public enum CustomerColumn implements TestColumn {
	ID("id"), EMAIL("email"), FULL_NAME("full_name"), LOYALTY_POINTS("loyalty_points"), STATUS("status"),
	EXTERNAL_ID("external_id");

	private final String sqlName;

	CustomerColumn(String sqlName) {
		this.sqlName = sqlName;
	}

	@Override
	public String getSqlName() {
		return sqlName;
	}
}
