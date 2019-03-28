package org.reactome.release;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.*;

public class UniProtReactomeEntryTest {
	private UniProtReactomeEntry uniProtReactomeEntry;

	@Before
	public void createUniProtReactomeEntry() {
		uniProtReactomeEntry = UniProtReactomeEntry.get(1L, "test UniProt", "P12345");
	}

	@Test
	public void sameUniProtObjectIsEqual() {
		assertThat(
			uniProtReactomeEntry,
			sameInstance(UniProtReactomeEntry.get(1L, "test UniProt", "P12345"))
		);
	}

	@Test
	public void differentUniProtObjectsWithDifferentValuesNotEqual() {
		assertNotEquals(uniProtReactomeEntry, UniProtReactomeEntry.get(2L, "another test UniProt", "Q54321"));
	}
}