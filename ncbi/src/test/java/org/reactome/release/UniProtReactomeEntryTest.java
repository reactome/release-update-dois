package org.reactome.release;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.*;

public class UniProtReactomeEntryTest {
	private UniProtReactomeEntry uniProtReactomeEntry;

	@BeforeEach
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
	public void sortsByUniProtAccessionAscendingly() {
		List<UniProtReactomeEntry> uniProtReactomeEntries = new ArrayList<>();
		uniProtReactomeEntries.add(uniProtReactomeEntry);
		UniProtReactomeEntry uniProtReactomeEntry2 = UniProtReactomeEntry.get(5L, "A01234", "UniProt:A01234");
		uniProtReactomeEntries.add(uniProtReactomeEntry2);

		Collections.sort(uniProtReactomeEntries);

		assertThat(uniProtReactomeEntries, contains(uniProtReactomeEntry2, uniProtReactomeEntry));
	}

	@Test
	public void tenCharacterAccessionIsAccepted() {
		final String ACCESSION = "P123456789";
		UniProtReactomeEntry uniprot = UniProtReactomeEntry.get(6L, ACCESSION, "UniProt:test");
		assertThat(uniprot.getAccession() , equalTo(ACCESSION));
	}

	@Test
	public void incorrectAccessionThrowsIllegalArgumentException() {
		IllegalArgumentException thrown = assertThrows(
			IllegalArgumentException.class,
			() -> UniProtReactomeEntry.get(7L, "P123456", "UniProt:testing"),
			"Expected call to 'UniProtReactomeEntry.get' to throw due to improper UniProt accession, but it didn't"
		);

		assertTrue(thrown.getMessage().contains("not a proper UniProt Accession"));
	}

	@Test
	public void incorrectDisplayNameThrowsIllegalArgumentException() {
		IllegalArgumentException thrown = assertThrows(
			IllegalArgumentException.class,
			() -> UniProtReactomeEntry.get(8L, "P12345", "testing"),
			"Expected call to 'UniProtReactomeEntry.get' to throw due to improper UniProt display name, but it didn't"
		);

		assertTrue(thrown.getMessage().contains("not a proper UniProt Display Name"));
	}
}