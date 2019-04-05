package org.reactome.release;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

public class UtilitiesTest {

	@Test
	public void splitSetWithEvenSubSetSizes() {
		Set<String> stringSet = getDummyStringSet();

		List<Set<String>> listOfStringSubSets = Utilities.splitSet(stringSet, 4);

		assertThat(listOfStringSubSets, hasSize(4));
		assertThat(listOfStringSubSets, everyItem(hasSetSize(2)));
	}

	@Test
	public void splitSetWithUnEvenSubSetSizes() {
		Set<String> stringSet = getDummyStringSet();

		List<Set<String>> listOfStringSubSets = Utilities.splitSet(stringSet, 3);

		assertThat(listOfStringSubSets, hasSize(3));
		assertThat(listOfStringSubSets.get(0), hasSize(3));
		assertThat(listOfStringSubSets.get(1), hasSize(3));
		assertThat(listOfStringSubSets.get(2), hasSize(2));
	}

	private Set<String> getDummyStringSet() {
		return new HashSet<>(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h"));
	}

	private Matcher<Set<String>> hasSetSize(int size) {
		return new TypeSafeMatcher<Set<String>>() {
			@Override
			protected boolean matchesSafely(Set<String> item) {
				return item.size() == size;
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("size of string set is " + size);
			}
		};
	}
}
