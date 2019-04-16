package org.reactome.release.dataexport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.reactome.release.common.ReleaseStep;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

/**
 * Release step to generate post-release export files for NCBI, UCSC and Europe PMC.
 * @author jweiser
 */
public class DataExporterStep extends ReleaseStep {
	private static final Logger logger = LogManager.getLogger();

	/**
	 * Queries the Reactome Neo4J Graph Database for the current release version and
	 * generates export files.  The files generated are:
	 * NCBI Gene XML
	 *     "Link" XML nodes describing NCBI Gene Identifier relationships to either UniProt entries or Top Level
	 *     Pathways in Reactome
	 * NCBI Gene Protein File (not uploaded to NCBI)
	 *     Tab delimited file of UniProt accession to NCBI Gene identifiers in Reactome
	 * NCBI Protein File
	 *     Entries of all UniProt accessions in Reactome associated with an NCBI Gene Identifier (file only contains
	 *     UniProt accessions)
	 * UCSC Entity File
	 *     Entries of all human, rat, and mouse UniProt accessions in Reactome
	 * UCSC Event File
	 *     UniProt accessions (human, rat, and mouse) mapped to the events in which they participate in Reactome
	 * Europe PMC Profile File
	 *     Short XML file identifying Reactome as a data provider to Europe PMC
	 * Europe PMC Link File
	 *     "Link" XML nodes describing Reactome Pathways connected to PubMed literature references
	 * @param props Configuration options for connecting to the graph database and writing output files
	 * @throws Exception Thrown if creation of the output directory
	 */
	@Override
	public void executeStep(Properties props) throws Exception {
		logger.info("Beginning NCBI, UCSC, and Europe PMC export step...");

		int version = Integer.parseInt(props.getProperty("reactomeVersion"));
		String outputDir = props.getProperty("outputDir", "archive");
		Files.createDirectories(Paths.get(outputDir));
		logger.info("Files for Reactome version " + version + " will be output to the directory " + outputDir);

		try (Session graphDBSession = getGraphDBDriver(props).session()) {
			List<NCBIEntry> ncbiEntries = NCBIEntry.getUniProtToNCBIGeneEntries(graphDBSession);

			// Write NCBI Gene related Protein File
			NCBIGene.getInstance(ncbiEntries, outputDir, version).writeProteinFile();

			// Write NCBI Gene Files (split into multiple files to conform with 15MB upload maximum)
			NCBIGene.getInstance(ncbiEntries, outputDir, version).writeGeneXMLFiles(graphDBSession);

			// Write NCBI Protein File
			NCBIProtein.getInstance(ncbiEntries, outputDir, version).writeNCBIProteinFile();

			// Write UCSC Entity and Event Files
			UCSC.getInstance(outputDir, version).writeUCSCFiles(graphDBSession);
			// Write Europe PMC Profile and Link Files
			EuropePMC.getInstance(outputDir, version).writeEuropePMCFiles(graphDBSession);
		}

		logger.info("Finished NCBI, UCSC, and Europe PMC export step");
	}

	/**
	 * Parses connections options and returns the a Neo4J Driver object for the graph database
	 * @param props Properties object with graph database connection information
	 * @return Driver for the graph database being run by the Neo4J server
	 */
	private static Driver getGraphDBDriver(Properties props) {
		String host = props.getProperty("host","localhost");
		String port = props.getProperty("port", Integer.toString(7687));
		String user = props.getProperty("user", "neo4j");
		String password = props.getProperty("password", "root");

		return GraphDatabase.driver("bolt://" + host + ":" + port, AuthTokens.basic(user, password));
	}
}
