package org.reactome.release.dataexport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class UniProtReactomeEntryTest {
	private UniProtReactomeEntry uniProtReactomeEntry;
	private final String DUMMY_UNIPROT_ACCESSION = "O65432";
	private final String DUMMY_UNIPROT_DISPLAY_NAME = "UniProt:another test UniProt";

	@BeforeEach
	public void createUniProtReactomeEntry() {
		final long UNIPROT_DB_ID = 3L;
		final String UNIPROT_ACCESSION = "P12345";
		final String UNIPROT_DISPLAY_NAME = "UniProt:" + UNIPROT_ACCESSION;

		uniProtReactomeEntry = UniProtReactomeEntry.get(UNIPROT_DB_ID, UNIPROT_ACCESSION, UNIPROT_DISPLAY_NAME);
	}

	@Test
	public void sameUniProtObjectIsEqual() {
		assertThat(
			uniProtReactomeEntry,
			sameInstance(
				UniProtReactomeEntry.get(
					uniProtReactomeEntry.getDbId(),
					uniProtReactomeEntry.getAccession(),
					uniProtReactomeEntry.getDisplayName()
				)
			)
		);
	}

	@Test
	public void differentUniProtObjectsWithDifferentValuesNotEqual() {
		final String DIFFERENT_ACCESSION = "Q54321";
		final String DIFFERENT_DISPLAY_NAME = "UniProt:" + DIFFERENT_ACCESSION;

		assertThat(
			uniProtReactomeEntry,
			is(not(equalTo(UniProtReactomeEntry.get(4L, DIFFERENT_ACCESSION, DIFFERENT_DISPLAY_NAME))))
		);
	}

	@Test
	public void sortsByUniProtAccessionAscendingly() {
		final String ACCESSION_THAT_SHOULD_BE_FIRST = "A01234";

		List<UniProtReactomeEntry> uniProtReactomeEntries = new ArrayList<>();
		uniProtReactomeEntries.add(uniProtReactomeEntry);
		UniProtReactomeEntry uniProtReactomeEntry2 = UniProtReactomeEntry.get(
			5L,
			ACCESSION_THAT_SHOULD_BE_FIRST,
			DUMMY_UNIPROT_DISPLAY_NAME
		);
		uniProtReactomeEntries.add(uniProtReactomeEntry2);

		Collections.sort(uniProtReactomeEntries);

		assertThat(uniProtReactomeEntries, contains(uniProtReactomeEntry2, uniProtReactomeEntry));
	}

	@Test
	public void tenCharacterAccessionIsAccepted() {
		final String ACCESSION = "P123456789";
		UniProtReactomeEntry uniprot = UniProtReactomeEntry.get(6L, ACCESSION, DUMMY_UNIPROT_DISPLAY_NAME);

		assertThat(uniprot.getAccession() , equalTo(ACCESSION));
	}

	@Test
	public void incorrectAccessionThrowsIllegalArgumentException() {
		final String INCORRECT_ACCESSION = "P123456"; // 7 character accession is illegal in UniProt

		IllegalArgumentException thrown = assertThrows(
			IllegalArgumentException.class,
			() -> UniProtReactomeEntry.get(7L, INCORRECT_ACCESSION, DUMMY_UNIPROT_DISPLAY_NAME),
			"Expected call to 'UniProtReactomeEntry.get' to throw due to improper UniProt accession, but it didn't"
		);

		assertThat(thrown.getMessage(), containsString("not a proper UniProt Accession"));
	}

	@Test
	public void incorrectDisplayNameThrowsIllegalArgumentException() {
		final String INCORRECT_DISPLAY_NAME = "testing"; // Display name must begin with "UniProt:"

		IllegalArgumentException thrown = assertThrows(
			IllegalArgumentException.class,
			() -> UniProtReactomeEntry.get(8L, DUMMY_UNIPROT_ACCESSION, INCORRECT_DISPLAY_NAME),
			"Expected call to 'UniProtReactomeEntry.get' to throw due to improper UniProt display name, but it didn't"
		);

		assertThat(thrown.getMessage(), containsString("not a proper UniProt Display Name"));
	}


	@Test
	public void emptyUniProtToReactomeEventsMapFromEmptyDatabase() {
		DummyGraphDBServer dummyGraphDBServer = DummyGraphDBServer.getInstance();
		dummyGraphDBServer.initializeNeo4j();

		Map<String, Set<ReactomeEvent>> uniProtToReactomeEvents =
			UniProtReactomeEntry.fetchUniProtAccessionToReactomeEvents(dummyGraphDBServer.getSession());

		assertThat(uniProtToReactomeEvents, is(anEmptyMap()));
	}

	@Test
	public void correctUniProtToReactomeEventsMapRetrieved() {
		final long EVENT_DB_ID = 1640170L;
		final String EVENT_DISPLAY_NAME = "Cell Cycle";
		final String EVENT_STABLE_ID = "R-HSA-1640170";

		DummyGraphDBServer dummyGraphDBServer = DummyGraphDBServer.getInstance();
		dummyGraphDBServer.initializeNeo4j();
		dummyGraphDBServer.populateDummyGraphDB();

		Map<String, Set<ReactomeEvent>> uniProtToReactomeEvents =
			UniProtReactomeEntry.fetchUniProtAccessionToReactomeEvents(dummyGraphDBServer.getSession());

		assertThat(uniProtToReactomeEvents, aMapWithSize(1));

		Set<ReactomeEvent> eventsAttachedToUniProtInstance = uniProtToReactomeEvents.get("P04637");
		assertThat(eventsAttachedToUniProtInstance, hasSize(7));

		ReactomeEvent expectedEvent = new ReactomeEvent(EVENT_DB_ID, EVENT_DISPLAY_NAME, EVENT_STABLE_ID);
		assertThat(eventsAttachedToUniProtInstance, hasItem(expectedEvent));
	}
}