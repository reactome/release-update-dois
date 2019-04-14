package org.reactome.release;

import org.junit.jupiter.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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
	public void correctEuropePMCLinkXMLElement() throws ParserConfigurationException {
		final String PATHWAY_DISPLAY_NAME = "p53-Dependent G1 DNA Damage Response";
		final String PATHWAY_STABLE_ID = "R-HSA-69563";
		final String PATHWAY_URL = "https://www.reactome.org/PathwayBrowser/#/" + PATHWAY_STABLE_ID;
		final String PUBMED_ID = "9153395";

		String expectedXML = String.format(
			"<link providerId=\"1903\">" +
			"<resource><title>%s</title><url>%s</url></resource>" +
			"<record><source>MED</source><id>%s</id></record>" +
			"</link>",
			PATHWAY_DISPLAY_NAME, PATHWAY_URL, PUBMED_ID
		);

		EuropePMC.EuropePMCLink europePMCLink = new EuropePMC.EuropePMCLink(
			PATHWAY_DISPLAY_NAME,
			PATHWAY_STABLE_ID,
			PUBMED_ID
		);
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		Element europePMCLinkXML = europePMCLink.getLinkXML(document);

		assertThat(getElementAsString(europePMCLinkXML), equalTo(expectedXML));
	}

	// Taken from https://stackoverflow.com/a/19701727
	private String getElementAsString(Element element) {
		DOMImplementationLS lsImpl = (DOMImplementationLS)element
			.getOwnerDocument()
			.getImplementation()
			.getFeature("LS", "3.0");
		LSSerializer serializer = lsImpl.createLSSerializer();
		//by default its true, so set it to false to get String without xml-declaration
		serializer.getDomConfig().setParameter("xml-declaration", false);
		return serializer.writeToString(element);
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
