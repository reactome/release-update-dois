package org.reactome.release;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.*;

public class UniProtReactomeEntryTest {
	private UniProtReactomeEntry uniProtReactomeEntry;

	@Before
	public void createUniProtReactomeEntry() {
		uniProtReactomeEntry = UniProtReactomeEntry.get(3L, "P12345", "UniProt:test UniProt");
	}

	@Test
	public void sameUniProtObjectIsEqual() {
		assertThat(
			uniProtReactomeEntry,
			sameInstance(UniProtReactomeEntry.get(3L, "P12345", "UniProt:test UniProt"))
		);
	}

	@Test
	public void differentUniProtObjectsWithDifferentValuesNotEqual() {
		assertNotEquals(uniProtReactomeEntry, UniProtReactomeEntry.get(4L, "Q54321", "UniProt:another test UniProt"));
	}
}