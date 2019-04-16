package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static org.reactome.release.DataExportUtilities.*;

/**
 * File generator for NCBI Gene.  This class has logic for producing a file for
 * NCBI Gene XML, describing the relationship between NCBI Gene identifiers and
 * Reactome UniProt entries as well as their top level pathways.
 * It also can create a file for NCBI Protein, describing the relationship between
 * NCBI Gene identifiers and Reactome UniProt entries in a simple tab delimited format.
 * @author jweiser
 */
public class NCBIGene {
	private static final Logger logger = LogManager.getLogger();
	private static final Logger ncbiGeneLogger = LogManager.getLogger("ncbiGeneLog");

	private static final String rootTag = "LinkSet";

	private List<NCBIEntry> ncbiEntries;
	private String outputDir;
	private int reactomeVersion;

	public static NCBIGene getInstance(List<NCBIEntry> ncbiEntries, String outputDir, int reactomeVersion) {
		return new NCBIGene(ncbiEntries, outputDir, reactomeVersion);
	}

	private NCBIGene(List<NCBIEntry> ncbiEntries, String outputDir, int reactomeVersion) {
		this.ncbiEntries = ncbiEntries;
		this.outputDir = outputDir;
		this.reactomeVersion = reactomeVersion;
	}

	/**
	 * Writes NCBI Protein tab-delimited file describing the UniProt to NCBI Gene identifier relationships in
	 * Reactome to a pre-set output directory
	 * @throws IOException Thrown if creating or appending for file fails
	 */
	public void writeProteinFile() throws IOException {
		logger.info("Writing proteins_version file");

		Path filePath = getProteinFilePath();
		deleteAndCreateFile(filePath);

		// Write file header
		Files.write(
			filePath,
			"UniProt ID\tGene id"
				.concat(System.lineSeparator())
				.concat(System.lineSeparator())
				.getBytes()
		);

		// Append map contents
		Set<String> proteinLines = new LinkedHashSet<>();
		for (NCBIEntry ncbiEntry : ncbiEntries) {
			for (String ncbiGeneId : ncbiEntry.getNcbiGeneIds()) {
				proteinLines.add(ncbiEntry.getUniprotAccession() + "\t" + ncbiGeneId);
			}
		}

		for (String line : proteinLines) {
			appendWithNewLine(line, filePath);
		}

		logger.info("Finished writing proteins_version file");
	}

	/**
	 * Writes NCBI Gene XML files describing the relationships between NCBI Gene identifiers and UniProt entries as
	 * well as their Reactome pathways to pre-set output directory
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @throws IOException Thrown if creating or appending for any file fails
	 */
	public void writeGeneXMLFiles(Session graphDBSession) throws IOException {

		logger.info("Writing gene XML file(s)");

		Path geneErrorFilePath = getGeneErrorFilePath();
		deleteAndCreateFile(geneErrorFilePath);

		Set<String> ncbiGeneXMLNodeStrings = new LinkedHashSet<>();
		for (NCBIEntry ncbiEntry : ncbiEntries) {
			ncbiGeneLogger.info("Working on " + ncbiEntry.getUniprotAccession());

			Set<ReactomeEvent> topLevelPathways = ncbiEntry.getTopLevelPathways(graphDBSession);
			if (topLevelPathways.isEmpty()) {
				String errorMessage = ncbiEntry.getUniprotDisplayName() +
									  " participates in Event(s) but no top Pathway can be found, i.e. there seem to be a pathway" +
									  " which contains or is an instance of itself.\n";

				Files.write(geneErrorFilePath, errorMessage.getBytes(), StandardOpenOption.APPEND);
				continue;
			}

			for (String ncbiGeneId : ncbiEntry.getNcbiGeneIds()) {
				ncbiGeneXMLNodeStrings.add(ncbiEntry.getEntityLinkXML(ncbiGeneId));

				for (ReactomeEvent topLevelPathway : topLevelPathways) {
					ncbiGeneXMLNodeStrings.add(ncbiEntry.getEventLinkXML(ncbiGeneId, topLevelPathway));
				}
			}

			ncbiGeneLogger.info("Finished with " + ncbiEntry.getUniprotAccession());
		}

		int fileCount = 0;
		int numberOfGeneXMLFiles = getNumberOfGeneXMLFiles(ncbiGeneXMLNodeStrings);
		for (Set<String> ncbiGeneXMLNodeStringsSubSet : splitSet(ncbiGeneXMLNodeStrings, numberOfGeneXMLFiles)) {
			Path geneXMLFilePath = getGeneXMLFilePath(++fileCount);

			deleteAndCreateFile(geneXMLFilePath);

			logger.info("Generating " + geneXMLFilePath.getFileName());

			appendWithNewLine(getXMLHeader(), geneXMLFilePath);
			appendWithNewLine(getOpenRootTag(), geneXMLFilePath);
			for (String ncbiGeneXMLNodeString : ncbiGeneXMLNodeStringsSubSet) {
				appendWithNewLine(ncbiGeneXMLNodeString, geneXMLFilePath);
			}

			appendWithNewLine(getCloseRootTag(), geneXMLFilePath);
		}

		logger.info("Finished writing gene XML file(s)");
	}

	/**
	 * The size (in bytes) for the set of all Link XML node strings to include in the final NCBI Gene XML files
	 * is determined and the minimum number of files to write in order for each to be less than the pre-set limit is
	 * returned.
	 * The maximum size of each file is 15MB and is a limit set by NCBI for individual file uploads.
	 * @param ncbiGeneXMLNodeStrings Set of all the Link XML nodes strings to include in the NCBI Gene XML file(s)
	 * @return Number of NCBI Gene XML files to create (i.e. split the XML nodes amongst to be under the file size
	 * limit)
	 */
	private int getNumberOfGeneXMLFiles(Set<String> ncbiGeneXMLNodeStrings) {
		final int BYTES_TO_KILOBYTES = 1024;
		final int KILOBYTES_TO_MEGABYTES = 1024;
		final double MAX_FILE_SIZE_IN_MEGABYTES = 14.0; // Actual limit is 15MB, but set lower for a bit of buffer space

		int sizeInBytes = ncbiGeneXMLNodeStrings.stream().mapToInt(str -> str.getBytes().length).sum();
		int sizeInMegaBytes = sizeInBytes / BYTES_TO_KILOBYTES / KILOBYTES_TO_MEGABYTES;

		// The number of files is rounded up to ensure the minimum number of files is an integer
		return (int) Math.ceil(sizeInMegaBytes / MAX_FILE_SIZE_IN_MEGABYTES);
	}

	private Path getProteinFilePath() {
		return Paths.get(outputDir, "proteins_version" + reactomeVersion);
	}

	private Path getGeneXMLFilePath(int fileCount) {
		String fileName = "gene_reactome" + reactomeVersion + "-" + fileCount + ".xml";
		return Paths.get(outputDir, fileName);
	}

	private Path getGeneErrorFilePath() {
		return Paths.get(outputDir, "geneentrez_" + reactomeVersion + ".err");
	}

	/**
	 * Returns the XML header for the NCBI Gene file containing the Reactome entity and event base URLs
	 * @return XML header as String
	 */
	public static String getXMLHeader() {
		return String.join(System.lineSeparator(),
			"<?xml version=\"1.0\"?>",
			"<!DOCTYPE LinkSet PUBLIC \"-//NLM//DTD LinkOut 1.0//EN\"",
			"\"http://www.ncbi.nlm.nih.gov/entrez/linkout/doc/LinkOut.dtd\"",
			"[",
			"<!ENTITY entity.base.url \"" + ReactomeURLConstants.UNIPROT_QUERY_URL + "\">",
			"<!ENTITY event.base.url \"" + ReactomeURLConstants.PATHWAY_BROWSER_URL + "\">",
			"]>"
		).concat(System.lineSeparator());
	}

	/**
	 * Returns the opening XML root level tag for the NCBI Gene file
	 * @return XML opening root level tag as String
	 */
	public static String getOpenRootTag() {
		return "<" + rootTag + ">";
	}

	/**
	 * Returns the closing XML root level tag for the NCBI Gene file
	 * @return XML closing root level tag as String
	 */
	public static String getCloseRootTag() {
		return "</" + rootTag + ">";
	}
}