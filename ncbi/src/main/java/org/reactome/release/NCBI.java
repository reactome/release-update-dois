package org.reactome.release;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class NCBI {
	private static final Logger logger = LogManager.getLogger();
	private static List<NCBIEntry> ncbiEntries;
	private static int version;
	private static String outputDir;

	public static void main( String[] args ) throws IOException {
		logger.info("Beginning NCBI step...");

		String pathToResources = args.length > 0 ? args[0] : "ncbi/src/main/resources/sample_config.properties";
		Properties props = new Properties();
		try {
			props.load(new FileInputStream(pathToResources));
		} catch (IOException e) {
			e.printStackTrace();
		}
		version = Integer.parseInt(props.getProperty("reactomeVersion", "67"));
		outputDir = props.getProperty("outputDir", "archive");
		Files.createDirectories(Paths.get(outputDir));
		System.out.println("Files for Reactome version " + version + " will be output to the directory " + outputDir);

		Session graphDBSession = getGraphDBDriver(props).session();
		System.out.println("Generating UniProt accession to NCBI Gene mapping");
		ncbiEntries = getUniProtToNCBIGeneMap(graphDBSession);

		System.out.println("Writing proteins_version file");
		writeProteinFile("proteins_version" + version);

		int numGeneXMLFiles = Integer.parseInt(props.getProperty("numGeneXMLFiles", "1"));
		System.out.println("Writing gene XML file(s)");
		writeGeneXMLFiles(graphDBSession, numGeneXMLFiles);

		System.out.println("Writing " + getNCBIProteinFilePath().getFileName());
		writeNCBIProteinFile();

		System.out.println("Writing UCSC files");
		writeUCSCFiles(graphDBSession);

		graphDBSession.close();
		logger.info("Finished NCBI step");
		System.exit(0);
	}

	private static void writeProteinFile(String fileName) throws IOException {
		Path filePath = Paths.get(outputDir, fileName);
		Files.deleteIfExists(filePath);
		Files.createFile(filePath);

		// Write file header
		Files.write(filePath, "UniProt ID\tGeneID\n\n".getBytes());

		// Append map contents
		for (NCBIEntry ncbiEntry : ncbiEntries) {
			for (String ncbiGeneId : ncbiEntry.getNcbiGeneIds()) {
				String line = ncbiEntry.getUniprotAccession() + "\t" + ncbiGeneId + "\n";
				Files.write(filePath, line.getBytes(), StandardOpenOption.APPEND);
			}
		}
	}

	private static List<NCBIEntry> getUniProtToNCBIGeneMap(Session graphDBSession) {
		StatementResult result = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (rgp:ReferenceGeneProduct)-[:referenceDatabase]->(rd:ReferenceDatabase)",
				"MATCH (rgp)-[:referenceGene]->(rds:ReferenceDNASequence)",
				"WHERE rd.displayName = 'UniProt' AND rds.databaseName = 'NCBI Gene'",
				"RETURN rgp.dbId, rgp.displayName, rgp.identifier, rds.identifier",
				"ORDER BY rgp.identifier;"
			)
		);

		Map<UniProtReactomeEntry, Set<String>> uniprotToNCBIGene = new HashMap<>();
		while (result.hasNext()) {
			Record record = result.next();
			long uniprotDbId = record.get("rgp.dbId").asLong();
			String uniprotDisplayName = record.get("rgp.displayName").asString();
			String uniprotAccession = record.get("rgp.identifier").asString();
			String ncbiGeneID = record.get("rds.identifier").asString();

			UniProtReactomeEntry uniprot = UniProtReactomeEntry.get(uniprotDbId, uniprotDisplayName, uniprotAccession);
			Set<String> ncbiGeneIDs = uniprotToNCBIGene.computeIfAbsent(uniprot, k -> new HashSet<>());
			ncbiGeneIDs.add(ncbiGeneID);
		}

		List<NCBIEntry> ncbiEntries = new ArrayList<>();
		for (UniProtReactomeEntry uniprot : uniprotToNCBIGene.keySet()) {
			ncbiEntries.add(
				new NCBIEntry(uniprot, uniprotToNCBIGene.get(uniprot))
			);
		}
		Collections.sort(ncbiEntries);
		return ncbiEntries;
	}

	private static void writeGeneXMLFiles(Session graphDBSession, int numGeneXMLFiles) throws IOException {
		Path geneErrorFilePath = Paths.get(outputDir, "geneentrez_" + version + ".err");
		Files.deleteIfExists(geneErrorFilePath);
		Files.createFile(geneErrorFilePath);

		int fileCount = 0;
		for (List<NCBIEntry> ncbiEntrySubList : splitList(ncbiEntries, numGeneXMLFiles)) {
			Path geneXMLFilePath = getGeneXMLFilePath(++fileCount);
			Files.deleteIfExists(geneXMLFilePath);
			Files.createFile(geneXMLFilePath);

			System.out.println("Generating " + geneXMLFilePath.getFileName());
			Files.write(geneXMLFilePath, NCBIEntry.getXMLHeader().getBytes(), StandardOpenOption.APPEND);
			Files.write(
				geneXMLFilePath,
				NCBIEntry.getOpenRootTag().concat(System.lineSeparator()).getBytes(),
				StandardOpenOption.APPEND
			);
			for (NCBIEntry ncbiEntry: ncbiEntrySubList) {
				List<PathwayHierarchyUtilities.TopLevelPathway> topLevelPathways = ncbiEntry.getTopLevelPathways(graphDBSession);
				Set<PathwayHierarchyUtilities.ReactomeEvent> topLevelPathways =
					ncbiEntry.getTopLevelPathways(graphDBSession);
				if (topLevelPathways.isEmpty()) {
					String errorMessage = ncbiEntry.getUniprotDisplayName() +
					" participates in Event(s) but no top Pathway can be found, i.e. there seem to be a pathway" +
					" which contains or is an instance of itself.\n";

					Files.write(geneErrorFilePath, errorMessage.getBytes(), StandardOpenOption.APPEND);
					continue;
				}

				for (String ncbiGeneId : ncbiEntry.getNcbiGeneIds()) {
					Files.write(
						geneXMLFilePath,
						ncbiEntry.getEntityLinkXML(ncbiGeneId, ncbiEntry.getUniprotAccession()).getBytes(),
						StandardOpenOption.APPEND
					);

					for (PathwayHierarchyUtilities.ReactomeEvent topLevelPathway : topLevelPathways) {
						Files.write(
							geneXMLFilePath,
							ncbiEntry.getEventLinkXML(ncbiGeneId, topLevelPathway).getBytes(),
							StandardOpenOption.APPEND
						);
					}
				}
			}

			Files.write(geneXMLFilePath, NCBIEntry.getCloseRootTag().getBytes(), StandardOpenOption.APPEND);
		}
	}

	private static void writeNCBIProteinFile() throws IOException {
		Path ncbiProteinFilePath = getNCBIProteinFilePath();
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

	private static Path getNCBIProteinFilePath() {
		return Paths.get(outputDir, "protein_reactome" + version + ".ft");
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

	private static void writeUCSCFiles(Session graphDBSession) throws IOException {
		List<UniProtReactomeEntry> uniProtReactomeEntries = graphDBSession.run(
			String.join(System.lineSeparator(),
				"MATCH (ewas:EntityWithAccessionedSequence)-[:referenceEntity]->(rgp:ReferenceGeneProduct)" +
				"-[:referenceDatabase]->(rd:ReferenceDatabase)",
				"WHERE ewas.speciesName IN ['Homo sapiens', 'Rattus norvegicus', 'Mus musculus'] AND rd.displayName =" +
				" 'UniProt'",
				"RETURN rgp.dbId, rgp.identifier, rgp.displayName"
			)
		)
		.stream()
		.map(record -> UniProtReactomeEntry.get(
			record.get("rgp.dbId").asLong(),
			record.get("rgp.identifier").asString(),
			record.get("rgp.displayName").asString()
		))
		.collect(Collectors.toList());

		writeUCSCEntityFile(uniProtReactomeEntries);
		writeUCSCEventFile(graphDBSession, uniProtReactomeEntries);
	}

	private static void writeUCSCEntityFile(List<UniProtReactomeEntry> uniProtReactomeEntries) throws IOException {
		Path ucscEntityFilePath = Paths.get(outputDir, "ucsc_entity" + version);
		Files.deleteIfExists(ucscEntityFilePath);
		Files.createFile(ucscEntityFilePath);

		String ucscEntityHeader =
			"URL for entity_identifier: " + ReactomeConstants.UNIPROT_QUERY_URL +
			System.lineSeparator() + System.lineSeparator() +
			"Reactome Entity" +
			System.lineSeparator() + System.lineSeparator();

		Files.write(ucscEntityFilePath, ucscEntityHeader.getBytes(), StandardOpenOption.APPEND);
		for (UniProtReactomeEntry uniProtReactomeEntry : uniProtReactomeEntries) {
			Files.write(ucscEntityFilePath, uniProtReactomeEntry.getAccession().getBytes(), StandardOpenOption.APPEND);
		}
	}

	private static void writeUCSCEventFile(Session graphDBSession, List<UniProtReactomeEntry> uniProtReactomeEntries)
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
		for (UniProtReactomeEntry uniProtReactomeEntry : uniProtReactomeEntries) {
			Set<PathwayHierarchyUtilities.ReactomeEvent> reactomeEvents =
				uniProtReactomeEntry.getEvents(graphDBSession);
			if (reactomeEvents.isEmpty()) {
				String errorMessage = uniProtReactomeEntry.getDisplayName() +
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

	private static List<List<NCBIEntry>> splitList(List<NCBIEntry> list, int numOfSubLists) {
		int subListSize = list.size() / numOfSubLists ;
		int numberOfExtraKeys = list.size() % numOfSubLists;
		if (numberOfExtraKeys > 0) {
			subListSize += 1;
		}

		List<List<NCBIEntry>> splitLists = new ArrayList<>();

		List<NCBIEntry> subList = new ArrayList<>();
		int keyCount = 0;
		for(NCBIEntry ncbiEntry : list) {
			subList.add(ncbiEntry);
			keyCount += 1;

			// Sub map is "full" and the next sub map should be populated
			if (keyCount == subListSize) {
				splitLists.add(subList);
				subList = new ArrayList<>();
				keyCount = 0;

				if (numberOfExtraKeys > 0) {
					numberOfExtraKeys--;

					if (numberOfExtraKeys == 0) {
						subListSize--;
					}
				}
			}
		}

		return splitLists;
	}

	private static Path getGeneXMLFilePath(int fileCount) {
		String fileName = "gene_reactome" + version + "-" + fileCount + ".xml";
		return Paths.get(outputDir, fileName);
	}

	private static Driver getGraphDBDriver(Properties props) {
		String host = props.getProperty("host","localhost");
		String port = props.getProperty("port", Integer.toString(7687));
		String user = props.getProperty("user", "neo4j");
		String password = props.getProperty("password", "root");

		return GraphDatabase.driver("bolt://" + host + ":" + port, AuthTokens.basic(user, password));
	}
}