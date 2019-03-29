package org.reactome.release;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.*;

public class UniProtReactomeEntryTest {
	private UniProtReactomeEntry uniProtReactomeEntry;

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

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

	@Test
	public void tenCharacterAccessionIsAccepted() {
		final String ACCESSION = "P123456789";
		UniProtReactomeEntry uniprot = UniProtReactomeEntry.get(5L, ACCESSION, "UniProt:test");
		assertThat(uniprot.getAccession() , equalTo(ACCESSION));
	}

	@Test
	public void incorrectAccessionThrowsIllegalArgumentException() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("not a proper UniProt Accession");
		UniProtReactomeEntry.get(6L, "P123456", "UniProt:testing");
	}

	@Test
	public void incorrectDisplayNameThrowsIllegalArgumentException() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("not a proper UniProt Display Name");
		UniProtReactomeEntry.get(5L, "P12345", "testing");
	}
}