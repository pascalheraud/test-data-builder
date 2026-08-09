package ovh.heraud.testdatabuilder.bookstore;

import java.time.LocalDateTime;

/**
 * A tiny stand-in for a real project's own date-builder value type — exists
 * only to demonstrate {@link BookstoreTestDataBuilder#setDataColumn}
 * converting a project-specific value type based on a column's
 * {@link ovh.heraud.testdatabuilder.generic.TestColumn#targetType()}, the
 * same pattern a consumer uses for its own domain types (dates, money,
 * whatever the generic {@code Data.setColumn} shouldn't need to know about).
 */
public record BookstoreDate(LocalDateTime value) {
}
