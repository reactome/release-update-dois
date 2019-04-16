package org.reactome.release.dataexport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.reactome.release.dataexport.DataExportUtilities.splitSet;

public class UtilitiesTest {
	private Set<String> stringSet;

	@BeforeEach
	public void initializeSet() {
		this.stringSet = new HashSet<>(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h"));
	}

	@Test
	public void splitSetWithEvenSubSetSizes() {
		final int NUM_OF_SUB_SETS = 4;

		List<Set<String>> listOfStringSubSets = splitSet(stringSet, NUM_OF_SUB_SETS);

		assertThat(listOfStringSubSets, hasSize(NUM_OF_SUB_SETS));

		// Every set received even size of 2 elements
		assertThat(listOfStringSubSets, everyItem(hasSize(2)));
	}

	@Test
	public void splitSetWithUnEvenSubSetSizes() {
		final int NUM_OF_SUB_SETS = 3;

		List<Set<String>> listOfStringSubSets = splitSet(stringSet, NUM_OF_SUB_SETS);

		assertThat(listOfStringSubSets, hasSize(NUM_OF_SUB_SETS));

		// Three sets of size 3, 3, and 2
		assertThat(listOfStringSubSets.get(0), hasSize(3));
		assertThat(listOfStringSubSets.get(1), hasSize(3));
		assertThat(listOfStringSubSets.get(2), hasSize(2));
	}
}
