package org.reactome.release;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

public class NCBIProtein {
	private static final Logger logger = LogManager.getLogger();

	public static void writeNCBIProteinFile(List<NCBIEntry> ncbiEntries, String outputDir, int reactomeVersion)
		throws IOException {
		Path ncbiProteinFilePath = getNCBIProteinFilePath(outputDir, reactomeVersion);
		Files.deleteIfExists(ncbiProteinFilePath);
		Files.createFile(ncbiProteinFilePath);

		Files.write(ncbiProteinFilePath, getProteinFileHeader().getBytes(), StandardOpenOption.APPEND);

		List<String> proteinFileLines =
			ncbiEntries
				.stream()
				.map(NCBIEntry::getUniprotAccession)
				.map(uniprotId -> String.join("\t", "query:", uniprotId, "[pacc]").concat(System.lineSeparator()))
				.collect(Collectors.toList());
		for (String proteinFileLine : proteinFileLines) {
			Files.write(ncbiProteinFilePath, proteinFileLine.getBytes(), StandardOpenOption.APPEND);
		}

		Files.write(ncbiProteinFilePath, getProteinFileFooter().getBytes(), StandardOpenOption.APPEND);
	}

	private static Path getNCBIProteinFilePath(String outputDir, int reactomeVersion) {
		return Paths.get(outputDir, "protein_reactome" + reactomeVersion + ".ft");
	}

	private static String getProteinFileHeader() {
		return String.join(System.lineSeparator(),
			getProteinFileSeparator(),
			"prid:\t4914",
			"dbase:\tprotein",
			"stype:\tmeta-databases",
			"!base:\t" + ReactomeConstants.UNIPROT_QUERY_URL,
			getProteinFileSeparator(),
			"linkid:\t0"
		);
	}

	private static String getProteinFileFooter() {
		return String.join(System.lineSeparator(),
			"base:\t&base;",
			"rule:\t&lo.pacc;",
			getProteinFileSeparator()
		);
	}

	private static String getProteinFileSeparator() {
		final int TIMES_TO_REPEAT = 56;
		return StringUtils.repeat('-', TIMES_TO_REPEAT);
	}
}