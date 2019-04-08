package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.reactome.release.Utilities.appendWithNewLine;

/**
 * File generator for UCSC.  This class has logic for producing a file for
 * UCSC Entity, enumerating the UniProt entries (human, mouse, and rat) in Reactome and
 * UCSC Event, describing the relationship between UniProt entries (human, mouse, and rat)
 * in Reactome and the events in which they participate.
 * @author jweiser
 */
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

	/**
	 * Writes UCSC files describing the relationships of UniProt entries in Reactome as
	 * well as their Reactome pathways to pre-set output directory
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @throws IOException Thrown if creating or appending for any file fails
	 */
	public void writeUCSCFiles(Session graphDBSession) throws IOException {
		logger.info("Writing UCSC files");

		writeUCSCEntityFile(graphDBSession);
		writeUCSCEventFile(graphDBSession);

		logger.info("Finished writing UCSC files");
	}

	/**
	 * Writes UCSC Entity file describing UniProt entries in Reactome to pre-set output directory
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @throws IOException Thrown if creating or appending for file fails
	 */
	private void writeUCSCEntityFile(Session graphDBSession) throws IOException {
		Path ucscEntityFilePath = Paths.get(outputDir, "ucsc_entity" + version);
		Files.deleteIfExists(ucscEntityFilePath);
		Files.createFile(ucscEntityFilePath);

		logger.info("Writing UCSC Entity file");

		appendWithNewLine(getUCSCEntityHeader(), ucscEntityFilePath);
		for (String line : getUCSCEntityLines(graphDBSession)) {
			appendWithNewLine(line, ucscEntityFilePath);
		}

		logger.info("Finished writing UCSC Entity file");
	}

	/**
	 * Retrieves header describing the UCSC Entity File
	 * @return Header for UCSC Entity file as String
	 */
	private String getUCSCEntityHeader() {
		return "URL for entity_identifier: " + ReactomeConstants.UNIPROT_QUERY_URL +
			   System.lineSeparator() + System.lineSeparator() +
			   "Reactome Entity" +
			   System.lineSeparator() + System.lineSeparator();
	}

	/**
	 * Retrieves lines for the UCSC Entity File containing the accessions of all UniProt instances
	 * in Reactome with an EWAS of species human, rat, or mouse
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @return Set of Strings containing the lines for the UCSC Entity File
	 */
	private Set<String> getUCSCEntityLines(Session graphDBSession) {
		return getUniProtReactomeEntriesForUCSC(graphDBSession)
			.stream()
			.map(UniProtReactomeEntry::getAccession)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * Writes UCSC Event file describing UniProt entries in Reactome and the Events in which they participate
	 * to pre-set output directory
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @throws IOException Thrown if creating or appending for file fails
	 */
	private void writeUCSCEventFile(Session graphDBSession) throws IOException {
		Path ucscEventFilePath = Paths.get(outputDir, "ucsc_events" + version);
		Files.deleteIfExists(ucscEventFilePath);
		Files.createFile(ucscEventFilePath);

		Path ucscErrorFilePath = Paths.get(outputDir, "ucsc_" + version + ".err");
		Files.deleteIfExists(ucscErrorFilePath);
		Files.createFile(ucscErrorFilePath);

		logger.info("Writing UCSC Event file");

		appendWithNewLine(getUCSCEventsHeader(), ucscEventFilePath);

		Map<UniProtReactomeEntry, Set<String>> uniProtReactomeEntriesToUCSCEventLines =
			getUniProtReactomeEntriesToUCSCEventLines(graphDBSession);

		for (UniProtReactomeEntry uniProtReactomeEntry : uniProtReactomeEntriesToUCSCEventLines.keySet()) {
			Set<String> ucscLines = uniProtReactomeEntriesToUCSCEventLines.get(uniProtReactomeEntry);

			if (ucscLines.isEmpty()) {
				appendWithNewLine(getNoEventsErrorMessage(uniProtReactomeEntry), ucscErrorFilePath);
				continue;
			}

			for (String ucscLine : ucscLines) {
				appendWithNewLine(ucscLine, ucscEventFilePath);
			}
		}

		logger.info("Finished writing UCSC Event file");
	}

	/**
	 * Retrieves header describing the UCSC Event File
	 * @return Header for UCSC Event file as String
	 */
	private String getUCSCEventsHeader() {
		return "URL for events: " + ReactomeConstants.PATHWAY_BROWSER_URL +
			System.lineSeparator() + System.lineSeparator() +
			String.join("\t", "Reactome Entity", "Event ST_ID", "Event_name") +
			System.lineSeparator() + System.lineSeparator();
	}

	/**
	 * Retrieves Map of Reactome UniProt instance to lines for UCSC Event File for that UniProt instance
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @return Map of UniProt Reactome Entry to Set of Strings containing UCSC Event Lines
	 */
	private Map<UniProtReactomeEntry, Set<String>> getUniProtReactomeEntriesToUCSCEventLines(Session graphDBSession) {
		return getUniProtReactomeEntriesForUCSC(graphDBSession)
			.stream()
			.collect(Collectors.toMap(
				uniProtReactomeEntry -> uniProtReactomeEntry,
				uniProtReactomeEntry -> getUCSCEventLines(uniProtReactomeEntry, graphDBSession),
				(oldValue, newValue) -> newValue,
				TreeMap::new
			));
	}

	/**
	 * Retrieves lines, describing UniProt to Reactome Event relationships (including event stable identifier and
	 * display name), to include in the UCSC Event file for a given UniProt instance
	 * @param uniProtReactomeEntry UniProt instance in Reactome
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @return Set of UniProt Reactome Entry objects
	 */
	private Set<String> getUCSCEventLines(UniProtReactomeEntry uniProtReactomeEntry, Session graphDBSession) {
		return uniProtReactomeEntry
			.getEvents(graphDBSession)
			.stream()
			.map(event ->
				String.join(
					"\t",
					uniProtReactomeEntry.getAccession(),
					event.getStableIdentifier(),
					event.getName()
				)
			)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * Generates error message when a UniProt instance has no Reactome Events found in which it participates
	 * @param uniProtReactomeEntry UniProt instance in Reactome
	 * @return Error message for UniProt instance
	 */
	private String getNoEventsErrorMessage(UniProtReactomeEntry uniProtReactomeEntry) {
		return uniProtReactomeEntry.getDisplayName() +
			" participates in Event(s) but no top Pathway can be found, " +
			" i.e. there seem to be a pathway" +
			" which contains or is an instance of itself.";
	}

	/**
	 * Retrieves all UniProt instances in Reactome with an EWAS of species human, rat, or mouse
	 * @param graphDBSession Neo4J Driver Session object for querying the graph database
	 * @return Set of UniProt Reactome Entry objects
	 */
	Set<UniProtReactomeEntry> getUniProtReactomeEntriesForUCSC(Session graphDBSession) {
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