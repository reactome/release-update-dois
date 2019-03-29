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
import java.util.stream.Collectors;

public class UCSC {
	private static final Logger logger = LogManager.getLogger();

	private static Set<UniProtReactomeEntry> ucscUniProtReactomeEntries;
	private static int version;
	private static String outputDir;

	public static void writeUCSCFiles(Session graphDBSession) throws IOException {
		writeUCSCEntityFile(graphDBSession);
		writeUCSCEventFile(graphDBSession);
	}

	private static void writeUCSCEntityFile(Session graphDBSession) throws IOException {
		Path ucscEntityFilePath = Paths.get(outputDir, "ucsc_entity" + version);
		Files.deleteIfExists(ucscEntityFilePath);
		Files.createFile(ucscEntityFilePath);

		String ucscEntityHeader =
			"URL for entity_identifier: " + ReactomeConstants.UNIPROT_QUERY_URL +
			System.lineSeparator() + System.lineSeparator() +
			"Reactome Entity" +
			System.lineSeparator() + System.lineSeparator();

		Files.write(ucscEntityFilePath, ucscEntityHeader.getBytes(), StandardOpenOption.APPEND);
		for (UniProtReactomeEntry uniProtReactomeEntry : getUniProtReactomeEntriesForUCSC(graphDBSession)) {
			Files.write(ucscEntityFilePath, uniProtReactomeEntry.getAccession().getBytes(), StandardOpenOption.APPEND);
		}
	}

	private static void writeUCSCEventFile(Session graphDBSession)
		throws IOException {
		Path ucscEventFilePath = Paths.get(outputDir, "ucsc_events" + version);
		Files.deleteIfExists(ucscEventFilePath);
		Files.createFile(ucscEventFilePath);

		Path ucscErrorFilePath = Paths.get(outputDir, "ucsc_" + version + ".err");
		Files.deleteIfExists(ucscErrorFilePath);
		Files.createFile(ucscErrorFilePath);

		String ucscEventsHeader =
			"URL for events: " + ReactomeConstants.PATHWAY_BROWSER_URL +
			System.lineSeparator() + System.lineSeparator() +
			String.join("\t", "Reactome Entity", "Event ST_ID", "Event_name") +
			System.lineSeparator() + System.lineSeparator();

		Files.write(ucscEventFilePath, ucscEventsHeader.getBytes(), StandardOpenOption.APPEND);
		for (UniProtReactomeEntry uniProtReactomeEntry : getUniProtReactomeEntriesForUCSC(graphDBSession)) {
			Set<PathwayHierarchyUtilities.ReactomeEvent> reactomeEvents =
				uniProtReactomeEntry.getEvents(graphDBSession);
			if (reactomeEvents.isEmpty()) {
				String errorMessage =
					uniProtReactomeEntry.getDisplayName() +
					" participates in Event(s) but no top Pathway can be found, " +
					" i.e. there seem to be a pathway" +
					" which contains or is an instance of itself.\n";

				Files.write(ucscErrorFilePath, errorMessage.getBytes(), StandardOpenOption.APPEND);
				continue;
			}

			for (PathwayHierarchyUtilities.ReactomeEvent reactomeEvent : reactomeEvents) {
				String line = String.join("\t",
					uniProtReactomeEntry.getAccession(),
					reactomeEvent.getStableIdentifier(),
					reactomeEvent.getName()
				).concat(System.lineSeparator());

				Files.write(ucscEventFilePath, line.getBytes(), StandardOpenOption.APPEND);
			}
		}
	}

	private static Set<UniProtReactomeEntry> getUniProtReactomeEntriesForUCSC(Session graphDBSession) {
		if (ucscUniProtReactomeEntries != null) {
			return ucscUniProtReactomeEntries;
		}

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

		return ucscUniProtReactomeEntries;
	}
}