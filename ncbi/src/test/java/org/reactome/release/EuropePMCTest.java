package org.reactome.release;

import org.junit.jupiter.api.*;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class EuropePMCTest {

	@Test
	public void emptySetOfEuropePMCLinksFromEmptyDatabase() {
		DummyGraphDBServer dummyGraphDBServer = DummyGraphDBServer.getInstance();
		dummyGraphDBServer.initializeNeo4j();

		Set<EuropePMC.EuropePMCLink> europePMCLinks = EuropePMC.fetchEuropePMCLinks(dummyGraphDBServer.getSession());

		assertThat(europePMCLinks, is(empty()));
	}

	@Test
	public void singleEuropePMCLinkFromMockGraphDB() {
		DummyGraphDBServer dummyGraphDBServer = DummyGraphDBServer.getInstance();
		dummyGraphDBServer.initializeNeo4j();
		dummyGraphDBServer.populateDummyGraphDB();

		EuropePMC.EuropePMCLink expectedEuropePMCLink = new EuropePMC.EuropePMCLink(
			"p53-Dependent G1 DNA Damage Response",
			"R-HSA-69563",
			"9153395"
		);

		Set<EuropePMC.EuropePMCLink> europePMCLinks = EuropePMC.fetchEuropePMCLinks(dummyGraphDBServer.getSession());

		assertThat(europePMCLinks, contains(expectedEuropePMCLink));
	}

	@Test
	public void throwExceptionWithNonHumanStableId() {
		final String DUMMY_DISPLAY_NAME = "Pathway name";
		final String DUMMY_PUBMED_ID = "7654321";

		IllegalArgumentException thrown = assertThrows(
			IllegalArgumentException.class,
			() -> new EuropePMC.EuropePMCLink(DUMMY_DISPLAY_NAME,"R-MMU-12345",DUMMY_PUBMED_ID),
			"Expected creation of EuropePMCLink object to throw exception due to non-human stable id, but it didn't"
		);

		assertThat(thrown.getMessage(), containsString("not a human stable id"));
	}

	@Test
	public void throwExceptionWithNonNumericPubMedId() {
		final String DUMMY_DISPLAY_NAME = "Pathway name";
		final String DUMMY_STABLE_ID = "R-HSA-12345";

		IllegalArgumentException thrown = assertThrows(
			IllegalArgumentException.class,
			() -> new EuropePMC.EuropePMCLink(DUMMY_DISPLAY_NAME, DUMMY_STABLE_ID, "abc123"),
			"Expected creation of EuropePMCLink object to throw exception due to non-numeric PubMed id, but it didn't"
		);

		assertThat(thrown.getMessage(), containsString("is not numeric"));
	}

	@Test
	public void identicalObjectsAreEqual() {
		final String DISPLAY_NAME = "Pathway name";
		final String STABLE_ID = "R-HSA-12345";
		final String PUBMED_ID = "7654321";

		EuropePMC.EuropePMCLink europePMCLink1 = new EuropePMC.EuropePMCLink(DISPLAY_NAME, STABLE_ID, PUBMED_ID);
		EuropePMC.EuropePMCLink europePMCLink2 = new EuropePMC.EuropePMCLink(DISPLAY_NAME, STABLE_ID, PUBMED_ID);

		assertThat(europePMCLink1, equalTo(europePMCLink2));
	}
}
