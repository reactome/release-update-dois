package org.reactome.release;

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
		ncbiEntries = getUniProtToNCBIGeneMap(graphDBSession);

		System.out.println("Creating proteins_version file");
		writeProteinFile("proteins_version" + version);

		int numGeneXMLFiles = Integer.parseInt(props.getProperty("numGeneXMLFiles", "1"));
		System.out.println("Generating gene XML file(s)");
		generateGeneXMLFiles(graphDBSession, numGeneXMLFiles);

		logger.info("Finished NCBI step");
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

		Map<UniProtInfo, Set<String>> uniprotToNCBIGene = new HashMap<>();
		while (result.hasNext()) {
			Record record = result.next();
			UniProtInfo uniprot = new UniProtInfo(record);
			String ncbiGeneID = record.get("rds.identifier").asString();

			Set<String> ncbiGeneIDs = uniprotToNCBIGene.computeIfAbsent(uniprot, k -> new HashSet<>());
			ncbiGeneIDs.add(ncbiGeneID);
		}

		List<NCBIEntry> ncbiEntries = new ArrayList<>();
		for (UniProtInfo uniprot : uniprotToNCBIGene.keySet()) {
			ncbiEntries.add(
				new NCBIEntry(uniprot.getAccession(), uniprot.getDisplayName(), uniprotToNCBIGene.get(uniprot))
			);
		}
		Collections.sort(ncbiEntries);
		return ncbiEntries;
	}

	private static void generateGeneXMLFiles(Session graphDBSession, int numGeneXMLFiles) throws IOException {
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
				System.out.println("Getting top level pathways for " + ncbiEntry.getUniprotAccession());
				List<PathwayHierarchyUtilities.TopLevelPathway> topLevelPathways = ncbiEntry.getTopLevelPathways(graphDBSession);
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

					for (PathwayHierarchyUtilities.TopLevelPathway topLevelPathway : topLevelPathways) {
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

	public static class UniProtInfo {
		private long dbId;
		private String displayName;
		private String accession;

		public UniProtInfo(Record record) {
			this.dbId = record.get("rgp.dbId").asLong();
			this.displayName = record.get("rgp.displayName").asString();
			this.accession = record.get("rgp.identifier").asString();
		}

		public long getDbId() {
			return dbId;
		}

		public String getDisplayName() {
			return displayName;
		}

		public String getAccession() {
			return accession;
		}

		@Override
		public boolean equals(Object object) {
			if (object == this) {
				return true;
			}

			if (!(object instanceof UniProtInfo)) {
				return false;
			}

			UniProtInfo otherUniprot = (UniProtInfo) object;
			return
				getDbId() == otherUniprot.getDbId() &&
				getDisplayName().equals(otherUniprot.getDisplayName()) &&
				getAccession().equals(otherUniprot.getAccession());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getDbId(), getDisplayName(), getAccession());
		}
	}
}