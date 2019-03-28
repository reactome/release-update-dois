package org.reactome.release;

import org.junit.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

public class NCBIEntryTest {

	@Test
	public void sortsByUniProtAccessionAscendingly() {
		List<NCBIEntry> ncbiEntries = new ArrayList<>();
		NCBIEntry entry1 = new NCBIEntry(1L, "Q12345", "test UniProt", new HashSet<>(Arrays.asList("1", "2")));
		NCBIEntry entry2 = new NCBIEntry(2L, "P23456", "another test UniProt", new HashSet<>(Arrays.asList("3", "4")));
		ncbiEntries.add(entry1);
		ncbiEntries.add(entry2);

		Collections.sort(ncbiEntries);

		assertThat(ncbiEntries, contains(entry2, entry1));
	}
}