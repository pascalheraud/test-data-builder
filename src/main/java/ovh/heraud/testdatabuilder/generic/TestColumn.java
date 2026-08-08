package ovh.heraud.testdatabuilder.generic;

/**
 * A column usable by {@link Data#setColumn}/{@link Data#getColumn}, as a
 * typo-safe alternative to a raw string key. Implemented by a project-specific
 * enum, one constant per column of a table (mirrors {@link TestTable}).
 */
public interface TestColumn {

	/**
	 * The SQL column name.
	 *
	 * @return the SQL column name
	 */
	String getSqlName();

	/**
	 * The kind of column this is, used by {@link TestDataBuilder#setDataColumn} to
	 * convert a value before storing it. Defaults to {@link TestDataBuilder.TargetTypeEnum#TRANSPARENT}
	 * (no conversion) — a column enum overrides this only when it needs one.
	 *
	 * @return the target type this column converts values to, for {@link TestDataBuilder#setDataColumn}
	 */
	default TestDataBuilder.TargetTypeEnum targetType() {
		return TestDataBuilder.TargetTypeEnum.TRANSPARENT;
	}
}
