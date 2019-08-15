package org.reactome.release.dataexport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.reactome.release.dataexport.DataExportUtilities.*;

public class DataExportUtilitiesTest {
	private final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
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

	@Test
	public void createsXMLDocument() throws ParserConfigurationException {
		final String EXPECTED_XML_VERSION = "1.0";

		Document document = createXMLDocument();

		assertThat(document.getXmlStandalone(), is(true));
		assertThat(document.getXmlVersion(), is(equalTo(EXPECTED_XML_VERSION)));
	}

	@Test
	public void emptyXMLDocumentCreatesXMLString() throws ParserConfigurationException, TransformerException {
		Document document = createXMLDocument();

		String expectedXML = String.format("%s%n%n", XML_HEADER);

		assertThat(transformDocumentToXMLString(document), is(equalTo(expectedXML)));
	}

	@Test
	public void populatedXMLDocumentCreatesXMLString() throws ParserConfigurationException, TransformerException {
		final String INDENT = String.format("%4s", "");
		final String ROOT_ELEMENT_NAME = "root";
		final String CHILD_ELEMENT_NAME = "name";
		final String CHILD_ELEMENT_TEXT = "inner text";

		String expectedXML = String.join(System.lineSeparator(),
			XML_HEADER,
			String.format("<%s>", ROOT_ELEMENT_NAME),
			String.format("%s<%s>%s</%s>", INDENT, CHILD_ELEMENT_NAME, CHILD_ELEMENT_TEXT, CHILD_ELEMENT_NAME),
			String.format("</%s>%n", ROOT_ELEMENT_NAME)
		);

		Document document = createXMLDocument();
		Element rootElement = attachRootElement(document, ROOT_ELEMENT_NAME);
		Element childElement = getElement(document, CHILD_ELEMENT_NAME, CHILD_ELEMENT_TEXT);
		rootElement.appendChild(childElement);

		assertThat(transformDocumentToXMLString(document), is(equalTo(expectedXML)));
	}
}
