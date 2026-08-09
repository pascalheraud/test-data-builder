package ovh.heraud.testdatabuilder.bookstore;

import ovh.heraud.testdatabuilder.generic.TestColumn;
import ovh.heraud.testdatabuilder.generic.TestDataBuilder.TargetTypeEnum;

public enum OrderColumn implements TestColumn {
	ID("id"), CUSTOMER_ID("customer_id"), STATUS("status"),
	// TIMESTAMP: BookstoreTestDataBuilder.setDataColumn converts a BookstoreDate to
	// java.sql.Timestamp for this column — see newOrderForCurrentCustomer(BookstoreDate).
	PLACED_AT("placed_at") {
		@Override
		public TargetTypeEnum targetType() {
			return TargetTypeEnum.TIMESTAMP;
		}
	};

	private final String sqlName;

	OrderColumn(String sqlName) {
		this.sqlName = sqlName;
	}

	@Override
	public String getSqlName() {
		return sqlName;
	}
}
