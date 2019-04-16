package org.reactome.release;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.reactome.release.DataExportUtilities.appendWithNewLine;
import static org.reactome.release.DataExportUtilities.deleteAndCreateFile;

/**
 * File generator for NCBI Protein.  This class has logic for producing a file for
 * NCBI Protein, listing the UniProt entries in Reactome which are associated with an
 * NCBI Gene identifier (these are not listed in the file).
 * @author jweiser
 */
public class NCBIProtein {
	private static final Logger logger = LogManager.getLogger();

	private List<NCBIEntry> ncbiEntries;
	private String outputDir;
	private int reactomeVersion;

	public static NCBIProtein getInstance(List<NCBIEntry> ncbiEntries, String outputDir, int reactomeVersion) {
		return new NCBIProtein(ncbiEntries, outputDir, reactomeVersion);
	}

	private NCBIProtein(List<NCBIEntry> ncbiEntries, String outputDir, int reactomeVersion) {
		this.ncbiEntries = ncbiEntries;
		this.outputDir = outputDir;
		this.reactomeVersion = reactomeVersion;
	}

	/**
	 * Writes an NCBI Protein file describing the UniProt entries in Reactome with an NCBI Gene identifier (not listed)
	 * to a pre-set output directory
	 * @throws IOException Thrown if creating or appending for file fails
	 */
	public void writeNCBIProteinFile() throws IOException {
		logger.info("Writing NCBI protein file");

		Path ncbiProteinFilePath = getNCBIProteinFilePath();
		deleteAndCreateFile(ncbiProteinFilePath);

		appendWithNewLine(getProteinFileHeader(), ncbiProteinFilePath);

		for (String proteinFileLine : getProteinFileLines()) {
			appendWithNewLine(proteinFileLine, ncbiProteinFilePath);
		}

		appendWithNewLine(getProteinFileFooter(), ncbiProteinFilePath);

		logger.info("Finished writing NCBI protein file");
	}

	private Set<String> getProteinFileLines() {
		return ncbiEntries
			.stream()
			.map(NCBIEntry::getUniprotAccession)
			.map(uniprotId -> String.join("\t", "query:", uniprotId, "[pacc]"))
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private Path getNCBIProteinFilePath() {
		return Paths.get(outputDir, "protein_reactome" + reactomeVersion + ".ft");
	}

	private String getProteinFileHeader() {
		final int REACTOME_PROVIDER_ID_FOR_NCBI = 4914;

		return String.join(System.lineSeparator(),
			getProteinFileSeparator(),
			"prid:\t" + REACTOME_PROVIDER_ID_FOR_NCBI,
			"dbase:\tprotein",
			"stype:\tmeta-databases",
			"!base:\t" + ReactomeURLConstants.UNIPROT_QUERY_URL,
			getProteinFileSeparator(),
			"linkid:\t0"
		);
	}

	private String getProteinFileFooter() {
		return String.join(System.lineSeparator(),
			"base:\t&base;",
			"rule:\t&lo.pacc;",
			getProteinFileSeparator()
		);
	}

	private String getProteinFileSeparator() {
		final int TIMES_TO_REPEAT = 56;
		return StringUtils.repeat('-', TIMES_TO_REPEAT);
	}
}