package org.reactome.release.dataexport;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class UCSCTest {

	@Test
	public void noUniProtReactomeEntriesRetrievedFromEmptyDatabase() {
		final String DUMMY_DIR = "outputDir";
		final int DUMMY_REACTOME_VERSION = 999;

		DummyGraphDBServer dummyGraphDBServer = DummyGraphDBServer.getInstance();
		dummyGraphDBServer.initializeNeo4j();

		Set<UniProtReactomeEntry> uniProtReactomeEntries = UCSC.getInstance(DUMMY_DIR, DUMMY_REACTOME_VERSION)
			.getUniProtReactomeEntriesForUCSC(dummyGraphDBServer.getSession());

		assertThat(uniProtReactomeEntries, is(empty()));
	}

	@Test
	public void correctUniProtReactomeEntriesRetrieved() {
		final String DUMMY_DIR = "outputDir";
		final int DUMMY_REACTOME_VERSION = 999;

		final long UNIPROT_DB_ID = 69487L;
		final String UNIPROT_ACCESSION = "P04637";
		final String UNIPROT_DISPLAY_NAME = "UniProt:P04637 TP53";

		DummyGraphDBServer dummyGraphDBServer = DummyGraphDBServer.getInstance();
		dummyGraphDBServer.initializeNeo4j();
		dummyGraphDBServer.populateDummyGraphDB();

		Set<UniProtReactomeEntry> uniProtReactomeEntries = UCSC.getInstance(DUMMY_DIR, DUMMY_REACTOME_VERSION)
			.getUniProtReactomeEntriesForUCSC(dummyGraphDBServer.getSession());

		UniProtReactomeEntry expectedUniProtReactomeEntry =
			UniProtReactomeEntry.get(UNIPROT_DB_ID, UNIPROT_ACCESSION, UNIPROT_DISPLAY_NAME);

		assertThat(uniProtReactomeEntries, contains(expectedUniProtReactomeEntry));
	}
}
