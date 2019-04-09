package org.reactome.release.downloadDirectory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

public class Main {
	private static final Logger logger = LogManager.getLogger();

	public static void main(String[] args) throws Exception {

		logger.info("Beginning Download Directory step");
		String pathToConfig = "";
		if (args.length > 0) {
			pathToConfig = args[0];
		} else {
			pathToConfig = "src/main/resources/config.properties";
		}
		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));

		//TODO: Check stable identifiers db exists
		//TODO: File existence check and size check
		//TODO: Parallelize executions
		//TODO: Integration with Perl wrapper
		//TODO: Refactor to Command Pattern for execute calls

		//Set up DB adaptor
		String username = props.getProperty("username");
		String password = props.getProperty("password");
		String database = props.getProperty("database");
		String host = props.getProperty("host");
		int port = Integer.valueOf(props.getProperty("port"));
		String releaseNumber = props.getProperty("release");
		String releaseDirAbsolute = props.getProperty("absoluteReleaseDirectoryPath");
		String releaseDownloadDir = props.getProperty("releaseDownloadDirectoryPath");
		String releaseDownloadDirWithNumber = releaseDownloadDir + releaseNumber;
		MySQLAdaptor dbAdaptor = new MySQLAdaptor(host, database, username, password, port);
		File releaseDir = new File(releaseNumber);
		if (!releaseDir.exists())
		{
			releaseDir.mkdir();
		}

		String pathToSpeciesConfig = props.getProperty("speciesConfigPath");

		// Determine which steps will be run via stepsToRun.config file
		String pathToStepConfig = props.getProperty("stepsToRunConfigPath");
		FileReader fr = new FileReader(pathToStepConfig);
		BufferedReader br = new BufferedReader(fr);
		List<String> stepsToRun = br.lines().filter(
				line -> !line.startsWith("#")
			).collect(Collectors.toList());
		br.close();

		// Temporary system for catching failed steps -- this will need to be cleaned up in future
		List<String> failedSteps = new ArrayList<>();
		//Begin download directory steps
		if (stepsToRun.contains("DatabaseDumps"))
		{
			// This step takes a DB Dump of the stable_identifiers and test_reactome DBs
			// Outputs: gk_stable_ids.sql, gk_current.sql
			try {
				DatabaseDumps.execute(releaseNumber, username, password, host, port, database);
			} catch (Exception e) {
				failedSteps.add("DatabaseDumps");
				e.printStackTrace();
			}
		}
		if (stepsToRun.contains("BioPAX2") || stepsToRun.contains("BioPAX3"))
		{
			// This step runs BioPAX level 2 and BioPAX level 3 for Reactome's data using the Pathway-Exchange functions
			// Outputs: biopax2.zip and biopax2_validator.zip, and biopax.zip and biopax_validator.zip (for level 3)
			// These zip files should contain a number of species-specific 'owl' (BioPAX files) and 'xml' validation files
			List<Integer> biopaxLevels = new ArrayList<>();
			if (stepsToRun.contains("BioPAX2")) {
				biopaxLevels.add(2);
			}
			if (stepsToRun.contains("BioPAX3")) {
				biopaxLevels.add(3);
			}
			try {
				Biopax.execute(username, password, host, Integer.toString(port), database, releaseNumber, pathToSpeciesConfig, biopaxLevels);
			} catch (Exception e) {
				failedSteps.add("BioPAX");
				//TODO: Catch the specific exception when its a DataModel problem
				logger.warn("\nAn error was caught during BioPAX -- have you updated the Pathway-Exchange installation?");
				e.printStackTrace();
			}
		}
		if (stepsToRun.contains("GSEAOutput"))
		{
			// This step converts all Human Pathways to the MSigDB format used by GSEA
			// Output: ReactomePathways.gmt.zip
			try {
				GSEAOutput.execute(dbAdaptor, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("GSEAOutput");
				e.printStackTrace();
			}
		}

		if (stepsToRun.contains("FetchTestReactomeOntologyFiles"))
		{
			// This step, (formerly fetchEmptyProject), takes the blob output from the Ontology.ontology and parses it into 3 files
			// Outputs: reactome_data_model.pprj, reactome_data_model.pont, reactome_data_model.pins
			try {
				FetchTestReactomeOntologyFiles.execute(dbAdaptor, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("FetchTestReactomeOntologyFiles");
				e.printStackTrace();
			}
		}

		if (stepsToRun.contains("PathwaySummationMappingFile"))
		{
			// This step takes all Human Pathway and creates a tab-separated file with columns containing the stableIdentifier, name, and summation of the instance
			// Output: pathway2summation.txt
			try {
				PathwaySummationMappingFile.execute(dbAdaptor, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("PathwaySummationMappingFile");
				e.printStackTrace();
			}
		}
		if (stepsToRun.contains("MapOldStableIds"))
		{
			// This step iterates through all StableIdentifiers and maps them to the old Reactome ID in 'REACT_#####' format. Human instances are displayed first.
			// Output: reactome_stable_ids.txt
			try {
				MapOldStableIds.execute(username, password, host, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("MapOldStableIds");
				e.printStackTrace();
			}
		}
		// These file copy commands now use absolute paths instead of relative ones
		if (stepsToRun.contains("GenerateGOAnnotationFile"))
		{
			// This step copies the gene_association.reactome file generated during the goa_prepare step of Release to the download_directory folder
			// Output: gene_association.reactome
			try {
				GenerateGOAnnotationFile.execute(dbAdaptor, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("GenerateGOAnnotationFile");
				e.printStackTrace();
			}
		}
		if (stepsToRun.contains("models2pathways.tsv"))
		{
			// This step copies the models2pathways.tsv file generated during the biomodels step of Release to the download_directory folder
			logger.info("Copying models2pathways.tsv to release directory");
			try {
				Files.copy(Paths.get(releaseDirAbsolute + "biomodels/models2pathways.tsv"), Paths.get(releaseNumber + "/models2pathways.tsv"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception e) {
				failedSteps.add("models2pathways.tsv");
				e.printStackTrace();
			}
		}
		if (stepsToRun.contains("CreateReactome2BioSystems"))
		{
			// This step converts Reactome Pathway instances into the NCBI BioSystems format (http://www.ncbi.nlm.nih.gov/biosystems/).
			// Output: ReactomeToBioSystems.zip
			// This zip file contains an 'xml' file for each species in Reactome in BioSystems format
			try {
				CreateReactome2BioSystems.execute(host, database, username, password, port, releaseNumber);
			} catch (Exception e) {
				failedSteps.add("CreateReactome2BioSystems");
				e.printStackTrace();
			}
		}

		// Move files to downloadDirectory release folder
		logger.info("Moving all generated files to " + releaseDownloadDirWithNumber);
		File folder = new File(releaseNumber);
		File[] releaseFiles = folder.listFiles();
		if (releaseFiles != null) {
			for (File releaseFile : releaseFiles)
			{
				if (releaseFile.isDirectory() && releaseFile.getName().equalsIgnoreCase("databases"))
				{
					FileUtils.deleteDirectory(new File(releaseDownloadDirWithNumber + "/databases"));
				}

				Files.move(Paths.get(releaseFile.toString()), Paths.get(releaseDownloadDirWithNumber + "/" + releaseFile.getName()), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		if (failedSteps.size() > 0) {
			String failedStepsString = StringUtils.join(failedSteps, ", ");
			logger.warn("\nErrors were reported in the following step(s): " + failedStepsString + "\n");
		}
		logger.info("Finished DownloadDirectory");
	}
}


