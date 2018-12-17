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

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

public class Main {
	static MySQLAdaptor dbAdaptor = null;
	private static final Logger logger = LogManager.getLogger();
	
	public static void main(String[] args) throws Exception {
		
		logger.info("Beginning Download Directory step");
		String pathToConfig = "src/main/resources/config.properties";
		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));
		
		//TODO: Check stable identifiers db exists
		//TODO: Describe each functions outputs in documentation
		//TODO: File existence check and size check
		//TODO: Configurable runs
		//TODO: Parallelize executions
		//TODO: Integration with Perl wrapper
		
		//Set up DB adaptor
		String username = props.getProperty("username");
		String password = props.getProperty("password");
		String database = props.getProperty("database");
		String host = props.getProperty("host");
		int port = Integer.valueOf(props.getProperty("port"));
		String releaseNumber = props.getProperty("release") + "/";
		String releaseDirAbsolute = props.getProperty("absoluteReleaseDirectoryPath");
		String releaseDownloadDir = props.getProperty("releaseDownloadDirectoryPath");
		String releaseDownloadDirWithNumber = releaseDownloadDir + releaseNumber;
		dbAdaptor = new MySQLAdaptor(host, database, username, password, port);
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
		List<String> stepsToRun = new ArrayList<String>();
		String line;
		while ((line = br.readLine()) != null) 
		{
			if (!line.startsWith("#")) 
			{
				stepsToRun.add(line);
			}
		}
		br.close();
		
		//Begin download directory steps
		if (stepsToRun.contains("DatabaseDumps")) 
		{
			// This step takes a DB Dump of the stable_identifiers and test_reactome DBs
			// Outputs: gk_stable_ids.sql, gk_current.sql
			DatabaseDumps.execute(dbAdaptor, releaseNumber, username, password, host, port, database);
		}
		if (stepsToRun.contains("BioPAX2") || stepsToRun.contains("BioPAX3")) 
		{
			// This step runs BioPAX level 2 and BioPAX level 3 for Reactome's data using the Pathway-Exchange functinos
			// Outputs: biopax2.zip and biopax2_validator.zip, and biopax.zip and biopax_validator.zip (for level 3)
			// These zip files should contain a number of 'owl' files (BioPAX files) and 'xml' validation files
			Biopax.execute(username, password, host, Integer.toString(port), database, releaseNumber, pathToSpeciesConfig, stepsToRun.contains("BioPAX2"), stepsToRun.contains("BioPAX3"));
		}
		if (stepsToRun.contains("GSEAOutput")) 
		{
			// This step converts all Human Pathways to the MSigDB format used by GSEA
			// Output: ReactomePathways.gmt.zip
			GSEAOutput.execute(dbAdaptor, releaseNumber);
		}
		if (stepsToRun.contains("ReactomeBookPDF") || stepsToRun.contains("ReactomeBookRTF")) 
		{
			// This step currently calls the Perl scripts that generate the ReactomeBookPDF and ReactomeBookRTF
			//Outputs: TheReactomeBook.pdf.zip, TheReactomeBook.rtf.zip
			ReactomeBookGenerator.execute(username, password, host, port, database, releaseNumber, releaseDownloadDir, stepsToRun.contains("ReactomeBookPDF"), stepsToRun.contains("ReactomeBookRTF"));
		}
		if (stepsToRun.contains("FetchTestReactomeOntologyFiles")) 
		{
			// This step, (formerly fetchEmptyProject), takes the blob output from the Ontology.ontology and parses it into 3 files
			// Outputs: reactome_data_model.pprj, reactome_data_model.pont, reactome_data_model.pins
			FetchTestReactomeOntologyFiles.execute(dbAdaptor, username, password, host, database, releaseNumber);
		}
		if (stepsToRun.contains("CreateReleaseTarball")) 
		{
			// This step clones the Release repo from github, and generates an archive tarball from it and other files on the release server. Currently just runs make_release_tarball.pl.
			// Output: reactome.tar.gz 
			CreateReleaseTarball.execute(releaseNumber, releaseDownloadDir);
		}
		if (stepsToRun.contains("PathwaySummationMappingFile")) 
		{
			// This step takes all Human Pathway and creates a tab-seperated file with columns containining the stableIdentifier, name, and summation of the instance
			// Output: pasthway2summation.txt
			PathwaySummationMappingFile.execute(dbAdaptor, releaseNumber);
		}
		if (stepsToRun.contains("MapOldStableIds")) 
		{
			// This step iterates through all StableIdentifiers and maps them to the old Reactome ID in 'REACT_#####' format. Human instances are displayed first.
			// Output: reactome_stable_ids.txt
			MapOldStableIds.execute(username, password, host, releaseNumber);
		}
		// These file copy commands now use absolute paths instead of relative ones
		if (stepsToRun.contains("gene_association.reactome")) 
		{
			// This step copies the gene_association.reactome file generated during the goa_prepare step of Release to the download_directory folder
			// Output: gene_association.reactome
			logger.info("Copying gene_association.reactome to release directory");
			Files.copy(Paths.get(releaseDirAbsolute + "goa_prepare/gene_association.reactome"), Paths.get(releaseNumber + "gene_association.reactome"), StandardCopyOption.REPLACE_EXISTING);
		}
		if (stepsToRun.contains("models2pathways.tsv"))
		{
			// This step copies the models2pathways.tsv file generated during the biomodels step of Release to the download_directory folder
			logger.info("Copying models2pathways.tsv to release directory");
			Files.copy(Paths.get(releaseDirAbsolute + "biomodels/models2pathways.tsv"), Paths.get(releaseNumber + "models2pathways.tsv"), StandardCopyOption.REPLACE_EXISTING);
		}
		if (stepsToRun.contains("CreateReactome2BioSystems")) 
		{
			// This step converts Reactome Pathway instances into the NCBI BioSystems format (http://www.ncbi.nlm.nih.gov/biosystems/). 
			// Output: ReactomeToBioSystems.zip
			// This zip file contains an 'xml' file for each species in Reactome in BioSystems format 
			CreateReactome2BioSystems.execute(host, database, username, password, port, releaseNumber);
		}
		// Move files to downloadDirectory release folder
		logger.info("Moving all generated files to " + releaseDownloadDirWithNumber);
		File folder = new File(releaseNumber);
		File[] releaseFiles = folder.listFiles();
		for (int i = 0; i < releaseFiles.length; i++) 
		{
			System.out.println(releaseFiles[i]);
			if (releaseFiles[i].isDirectory() && releaseFiles[i].getName().equalsIgnoreCase("databases")) 
			{
				FileUtils.deleteDirectory(new File(releaseDownloadDirWithNumber + "/databases"));
			}
			
			Files.move(Paths.get(releaseFiles[i].toString()), Paths.get(releaseDownloadDirWithNumber + "/" + releaseFiles[i].getName()), StandardCopyOption.REPLACE_EXISTING); 
		}
		releaseNumber.replace("/", "");
		logger.info("Finished DownloadDirectory for release " + releaseNumber);
	}
}


