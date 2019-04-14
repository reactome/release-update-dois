package org.reactome.release;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * General Utilities class.  This class contains methods needed for general manipulation of data or common operations
 * used by the data export process.
 * @author jweiser
 */
public class Utilities {
	/**
	 * Takes a set of Strings and splits it into a list of the requested number of sub-sets.  If an ordered set, order
	 * is preserved.  If the set does not split evenly into the number of set-sets requested, the remainder elements
	 * will be added one element each to the beginning sets of the returned list.
	 * @param set Set of Strings to split
	 * @param numOfSubSets Number of sub-sets to return
	 * @return List of String sub-sets
	 */
	public static List<Set<String>> splitSet(Set<String> set, int numOfSubSets) {
		int subSetSize = set.size() / numOfSubSets ;
		int numberOfExtraKeys = set.size() % numOfSubSets;
		if (numberOfExtraKeys > 0) {
			subSetSize += 1;
		}

		List<Set<String>> splitSets = new ArrayList<>();

		Set<String> subSet = new LinkedHashSet<>();
		int keyCount = 0;
		for(String stringInSet : set) {
			subSet.add(stringInSet);
			keyCount += 1;

			// Sub set is "full" and the next sub set should be populated
			if (keyCount == subSetSize) {
				splitSets.add(subSet);
				subSet = new LinkedHashSet<>();
				keyCount = 0;

				if (numberOfExtraKeys > 0) {
					numberOfExtraKeys--;

					if (numberOfExtraKeys == 0) {
						subSetSize--;
					}
				}
			}
		}

		return splitSets;
	}

	/**
	 * Deletes the file at the path provided (if it exists) and creates an empty file
	 * @param filePath Path of file to delete (if it already exists) and re-create
	 * @throws IOException Thrown if unable to delete file (if exists) or create file at path provided
	 */
	public static void deleteAndCreateFile(Path filePath) throws IOException {
		Files.deleteIfExists(filePath);
		Files.createFile(filePath);
	}

	/**
	 * Appends a String value to a file, specified by path, with a new line character (determined by OS)
	 * @param lineToAppend Line to append to file
	 * @param filePath Path of file to which to append line
	 * @throws IOException Thrown if unable to append to file at path provided
	 */
	public static void appendWithNewLine(String lineToAppend, Path filePath) throws IOException {
		Files.write(
			filePath,
			lineToAppend.concat(System.lineSeparator()).getBytes(),
			StandardOpenOption.APPEND
		);
	}

	/**
	 * Appends a List of String values to a file, specified by path, with a new line character (determined by OS)
	 * added for each String value in the list
	 * @param linesToAppend Lines to append to file
	 * @param filePath Path of file to which to append line
	 * @throws IOException Thrown if unable to append to file at path provided
	 */
	public static void appendWithNewLine(Iterable<String> linesToAppend, Path filePath) throws IOException {
		for (String lineToAppend : linesToAppend) {
			appendWithNewLine(lineToAppend, filePath);
		}
	}

	/**
	 * Returns an empty W3C Document object for a standalone XML document
	 * @return W3C Document object representing an XML structure
	 * @throws ParserConfigurationException Thrown if the creation of the document builder object fails
	 */
	public static Document createXMLDocument() throws ParserConfigurationException {
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

		Document doc = documentBuilder.newDocument();
		doc.setXmlStandalone(true);

		return doc;
	}

	/**
	 * Creates, attaches (to the passed document object), and returns a root XML element
	 * @param document XML W3C Document
	 * @param elementName Name of root element to create and attach to the document
	 * @return W3C Element object representing the root element of the document
	 */
	public static Element attachRootElement(Document document, String elementName) {
		Element rootElement = document.createElement(elementName);
		document.appendChild(rootElement);

		return rootElement;
	}

	/**
	 * Creates and returns an XML element with the provided name and text content
	 * @param document XML W3C Document used to create the element
	 * @param elementName Name of the XML element to create (i.e. the tag name)
	 * @param elementText Value of the text to include as the element's child text node
	 * @return W3C Element object representing the created XML element
	 */
	public static Element getElement(Document document, String elementName, String elementText) {
		Element element = document.createElement(elementName);
		element.appendChild(document.createTextNode(elementText));

		return element;
	}

	/**
	 * Returns the XML String equivalent of an XML W3C Document object
	 * @param doc XML W3C Document
	 * @return XML String representation of the document
	 * @throws TransformerException Throws if the transformation of the document fails
	 */
	public static String transformDocumentToXMLString(Document doc) throws TransformerException {
		StringWriter writer = new StringWriter();
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer;
		transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
		transformer.transform(new DOMSource(doc), new StreamResult(writer));

		return writer.toString();
	}
}