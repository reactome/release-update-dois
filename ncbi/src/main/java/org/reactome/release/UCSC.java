package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.neo4j.driver.v1.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class UCSC {
	private static final Logger logger = LogManager.getLogger();

	private Set<UniProtReactomeEntry> ucscUniProtReactomeEntries;
	private int version;
	private String outputDir;

	public static UCSC getInstance(String outputDir, int version) {
		return new UCSC(outputDir, version);
	}

	private UCSC(String outputDir, int version) {
		this.outputDir = outputDir;
		this.version = version;
	}

	public void writeUCSCFiles(Session graphDBSession) throws IOException {
		logger.info("Writing UCSC files");

		writeUCSCEntityFile(graphDBSession);
		writeUCSCEventFile(graphDBSession);

		logger.info("Finished writing UCSC files");
	}

	private void writeUCSCEntityFile(Session graphDBSession) throws IOException {
		Path ucscEntityFilePath = Paths.get(outputDir, "ucsc_entity" + version);
		Files.deleteIfExists(ucscEntityFilePath);
		Files.createFile(ucscEntityFilePath);

		String ucscEntityHeader =
			"URL for entity_identifier: " + ReactomeConstants.UNIPROT_QUERY_URL +
			System.lineSeparator() + System.lineSeparator() +
			"Reactome Entity" +
			System.lineSeparator() + System.lineSeparator();

		logger.info("Writing UCSC Entity file");

		Files.write(ucscEntityFilePath, ucscEntityHeader.getBytes(), StandardOpenOption.APPEND);
		Set<String> ucscEntityLines = getUniProtReactomeEntriesForUCSC(graphDBSession)
			.stream()
			.map(entry -> entry.getAccession().concat(System.lineSeparator()))
			.collect(Collectors.toCollection(LinkedHashSet::new));
		for (String line : ucscEntityLines) {
			Files.write(ucscEntityFilePath, line.getBytes(), StandardOpenOption.APPEND);
		}

		logger.info("Finished writing UCSC Entity file");
	}

	private void writeUCSCEventFile(Session graphDBSession) throws IOException {
		Path ucscEventFilePath = Paths.get(outputDir, "ucsc_events" + version);
		Files.deleteIfExists(ucscEventFilePath);
		Files.createFile(ucscEventFilePath);

		Path ucscErrorFilePath = Paths.get(outputDir, "ucsc_" + version + ".err");
		Files.deleteIfExists(ucscErrorFilePath);
		Files.createFile(ucscErrorFilePath);

		logger.info("Writing UCSC Event file");

		Files.write(ucscEventFilePath, getUCSCEventsHeader().getBytes(), StandardOpenOption.APPEND);

		Set<String> ucscEventLines = new LinkedHashSet<>();
		for (UniProtReactomeEntry uniProtReactomeEntry : getUniProtReactomeEntriesForUCSC(graphDBSession)) {
			Set<ReactomeEvent> reactomeEvents =
				uniProtReactomeEntry.getEvents(graphDBSession);
			if (reactomeEvents.isEmpty()) {
				String errorMessage =
					uniProtReactomeEntry.getDisplayName() +
					" participates in Event(s) but no top Pathway can be found, " +
					" i.e. there seem to be a pathway" +
					" which contains or is an instance of itself." +
					System.lineSeparator();

				Files.write(ucscErrorFilePath, errorMessage.getBytes(), StandardOpenOption.APPEND);
				continue;
			}

			ucscEventLines.addAll(
				reactomeEvents
				.stream()
				.map(event -> String.join(
					"\t",
					uniProtReactomeEntry.getAccession(),
					event.getStableIdentifier(),
					event.getName()).concat(System.lineSeparator())
				)
				.collect(Collectors.toCollection(LinkedHashSet::new))
			);
		}


		for (String line : ucscEventLines) {
			Files.write(ucscEventFilePath, line.getBytes(), StandardOpenOption.APPEND);
		}
		logger.info("Finished writing UCSC Event file");
	}

	@NotNull
	private String getUCSCEventsHeader() {
		return "URL for events: " + ReactomeConstants.PATHWAY_BROWSER_URL +
			   System.lineSeparator() + System.lineSeparator() +
			   String.join("\t", "Reactome Entity", "Event ST_ID", "Event_name") +
			   System.lineSeparator() + System.lineSeparator();
	}

	private Set<UniProtReactomeEntry> getUniProtReactomeEntriesForUCSC(Session graphDBSession) {
		if (ucscUniProtReactomeEntries != null) {
			return ucscUniProtReactomeEntries;
		}

		logger.info("Fetching UniProt Reactome Entries for UCSC");

		ucscUniProtReactomeEntries = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (ewas:EntityWithAccessionedSequence)-[:referenceEntity]->(rgp:ReferenceGeneProduct)" +
				"-[:referenceDatabase]->(rd:ReferenceDatabase)",
				"WHERE ewas.speciesName IN ['Homo sapiens', 'Rattus norvegicus', 'Mus musculus'] AND rd.displayName =" +
				" 'UniProt'",
				"RETURN rgp.dbId, rgp.identifier, rgp.displayName",
				"ORDER BY rgp.identifier"
			)
		)
		.stream()
		.map(record -> UniProtReactomeEntry.get(
			record.get("rgp.dbId").asLong(),
			record.get("rgp.identifier").asString(),
			record.get("rgp.displayName").asString()
		))
		.collect(Collectors.toCollection(LinkedHashSet::new));

		logger.info("Finished fetching UniProt Reactome Entries for UCSC");

		return ucscUniProtReactomeEntries;
	}
}