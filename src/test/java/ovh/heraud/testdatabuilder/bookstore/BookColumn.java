package ovh.heraud.testdatabuilder.bookstore;

import ovh.heraud.testdatabuilder.generic.TestColumn;

public enum BookColumn implements TestColumn {
	ID("id"), TITLE("title"), ISBN("isbn"), PRICE("price"), PUBLISHER_ID("publisher_id");

	private final String sqlName;

	BookColumn(String sqlName) {
		this.sqlName = sqlName;
	}

	@Override
	public String getSqlName() {
		return sqlName;
	}
}
