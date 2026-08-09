package ovh.heraud.testdatabuilder.bookstore;

import ovh.heraud.testdatabuilder.generic.Data;

/** Every {@link Data} created by {@link BookstoreTestDataBuilder#newOrderWithItem()}. */
public record OrderWithItem(Data order, Data item) {
}
