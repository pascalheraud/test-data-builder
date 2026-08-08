package ovh.heraud.testdatabuilder.bookstore;

import ovh.heraud.testdatabuilder.generic.TestColumn;

public enum WarehouseColumn implements TestColumn {
	ID("id"), NAME("name"), CODE("code"), LATITUDE("latitude"), LONGITUDE("longitude");

	private final String sqlName;

	WarehouseColumn(String sqlName) {
		this.sqlName = sqlName;
	}

	@Override
	public String getSqlName() {
		return sqlName;
	}
}
