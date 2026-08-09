package ovh.heraud.testdatabuilder.generic;

/**
 * A column value that must be inserted as a raw SQL expression instead of a
 * plain bind parameter — e.g. {@code ST_GeomFromText(?, 4326)} for a PostGIS
 * geometry column. {@code sql} is inlined into the {@code INSERT} statement
 * in place of the column's placeholder; {@code params} are bound in its
 * place, in order.
 *
 * @param sql    the SQL expression, inlined verbatim in place of the column's placeholder
 * @param params the values bound in that expression's own placeholders, in order
 */
public record SqlExpression(String sql, Object... params) {
}
