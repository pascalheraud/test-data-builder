package ovh.heraud.testdatabuilder.bookstore;

import ovh.heraud.testdatabuilder.generic.TestColumn;

public enum PublisherColumn implements TestColumn {
	ID("id"), NAME("name"), COUNTRY("country");

	private final String sqlName;

	PublisherColumn(String sqlName) {
		this.sqlName = sqlName;
	}

	@Override
	public String getSqlName() {
		return sqlName;
	}
}
