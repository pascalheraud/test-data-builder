package ovh.heraud.testdatabuilder.generic;

/**
 * A table usable by {@link TestDataBuilder}. Implemented by a project-specific
 * enum, one constant per table.
 */
public interface TestTable {

	/**
	 * The SQL table name.
	 *
	 * @return the SQL table name
	 */
	String getSqlName();

	/**
	 * The columns of this table, e.g. {@code MyTableColumn.values()}.
	 *
	 * @return every column of this table
	 */
	TestColumn[] getColumns();
}
