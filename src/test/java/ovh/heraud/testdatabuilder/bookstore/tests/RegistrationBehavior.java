package ovh.heraud.testdatabuilder.bookstore.tests;

import java.util.List;

import org.junit.jupiter.api.Test;

import ovh.heraud.testdatabuilder.bookstore.BookstoreTable;
import ovh.heraud.testdatabuilder.bookstore.BookstoreTestDataBuilder;
import ovh.heraud.testdatabuilder.generic.Data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registration/naming behavior of {@link ovh.heraud.testdatabuilder.generic.TestDataBuilder},
 * shared across every vendor — a vendor's test class implements this
 * interface (and the other {@code *Behavior} interfaces) instead of
 * duplicating these {@code @Test} methods once per vendor.
 */
public interface RegistrationBehavior {

	BookstoreTestDataBuilder builder();

	@Test
	default void registeredDataIsRetrievableByNameAndTable() {
		// Given a publisher registered under the default name
		Data publisher = builder().newPublisher();

		// When looking it up by name and table
		Data found = builder().getData("root", BookstoreTable.PUBLISHER);
		List<Data> foundList = builder().getDataList("root", BookstoreTable.PUBLISHER);

		// Then it is retrievable, alone or in the full list for that table
		assertThat(found).isSameAs(publisher);
		assertThat(foundList).containsExactly(publisher);
	}

	@Test
	default void withNameScopesSubsequentRegistrations() {
		// Given a publisher under the default name, then one under a different name
		Data first = builder().newPublisher();
		builder().withName("second-scenario");
		Data second = builder().newPublisher();

		// When looking each up by its own name
		Data foundUnderRoot = builder().getData("root", BookstoreTable.PUBLISHER);
		Data foundUnderSecondScenario = builder().getData("second-scenario", BookstoreTable.PUBLISHER);

		// Then each name resolves only to its own publisher
		assertThat(foundUnderRoot).isSameAs(first);
		assertThat(foundUnderSecondScenario).isSameAs(second);

		// When switching back to the default name and registering another
		builder().withName("root");
		Data third = builder().newPublisher();
		List<Data> rootList = builder().getDataList("root", BookstoreTable.PUBLISHER);

		// Then the default name's list now holds both of its publishers, not the other name's
		assertThat(rootList).containsExactly(first, third);
	}

	@Test
	default void getDataThrowsWhenZeroOrMoreThanOneMatch() {
		// Given no publisher registered yet
		// When/Then looking one up throws
		assertThatThrownBy(() -> builder().getData("root", BookstoreTable.PUBLISHER))
				.isInstanceOf(IllegalStateException.class);

		// Given two publishers registered under the same name
		builder().newPublisher();
		builder().newPublisher();

		// When/Then looking up "the" publisher for that name also throws — there isn't exactly one
		assertThatThrownBy(() -> builder().getData("root", BookstoreTable.PUBLISHER))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	default void currentThrowsUntilSomethingIsRegisteredThenReturnsTheLatest() {
		// Given no publisher registered yet
		// When/Then a template relying on "current" throws
		assertThatThrownBy(() -> builder().newBookForCurrentPublisher())
				.isInstanceOf(IllegalStateException.class);

		// Given two publishers registered, the second one most recently
		builder().newPublisher();
		Data secondPublisher = builder().newPublisher();

		// When a book is created "for current"
		Data book = builder().newBookForCurrentPublisher();
		Data linkedPublisher = book.getColumn("publisher_id");

		// Then it links to the most recently created publisher, not the first one
		assertThat(linkedPublisher).isSameAs(secondPublisher);
	}
}
