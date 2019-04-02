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

public class NCBIGene {
	private static final Logger logger = LogManager.getLogger();
	private static final Logger ncbiGeneLogger = LogManager.getLogger("ncbiGeneLog");

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

	public void writeProteinFile() throws IOException {
		Path filePath = getProteinFilePath();

		Files.deleteIfExists(filePath);
		Files.createFile(filePath);

		logger.info("Writing proteins_version file");

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
				proteinLines.add(ncbiEntry.getUniprotAccession() + "\t" + ncbiGeneId + System.lineSeparator());
			}
		}

		for (String line : proteinLines) {
			Files.write(filePath, line.getBytes(), StandardOpenOption.APPEND);
		}

		logger.info("Finished writing proteins_version file");
	}

	public void writeGeneXMLFiles(Session graphDBSession, int numGeneXMLFiles) throws IOException {

		Path geneErrorFilePath = getGeneErrorFilePath();
		Files.deleteIfExists(geneErrorFilePath);
		Files.createFile(geneErrorFilePath);

		logger.info("Writing gene XML file(s)");

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
				ncbiGeneXMLNodeStrings.add(ncbiEntry.getEntityLinkXML(ncbiGeneId, ncbiEntry.getUniprotAccession()));

				for (ReactomeEvent topLevelPathway : topLevelPathways) {
					ncbiGeneXMLNodeStrings.add(ncbiEntry.getEventLinkXML(ncbiGeneId, topLevelPathway));
				}
			}

			ncbiGeneLogger.info("Finished with " + ncbiEntry.getUniprotAccession());
		}

		int fileCount = 0;
		for (Set<String> ncbiGeneXMLNodeStringsSubSet : Utilities.splitSet(ncbiGeneXMLNodeStrings, numGeneXMLFiles)) {
			Path geneXMLFilePath = getGeneXMLFilePath(++fileCount);
			Files.deleteIfExists(geneXMLFilePath);
			Files.createFile(geneXMLFilePath);

			logger.info("Generating " + geneXMLFilePath.getFileName());

			Files.write(geneXMLFilePath, NCBIEntry.getXMLHeader().getBytes(), StandardOpenOption.APPEND);
			Files.write(geneXMLFilePath, NCBIEntry.getOpenRootTag().concat(System.lineSeparator()).getBytes(),
					StandardOpenOption.APPEND);
			for (String ncbiGeneXMLNodeString : ncbiGeneXMLNodeStringsSubSet) {
				Files.write(geneXMLFilePath, ncbiGeneXMLNodeString.getBytes(), StandardOpenOption.APPEND);
			}

			Files.write(geneXMLFilePath, NCBIEntry.getCloseRootTag().getBytes(), StandardOpenOption.APPEND);
		}


		logger.info("Finished writing gene XML file(s)");
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
}