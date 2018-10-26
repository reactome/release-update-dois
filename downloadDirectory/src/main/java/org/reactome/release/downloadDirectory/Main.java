package org.reactome.release.downloadDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

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
		dbAdaptor = new MySQLAdaptor(host, database, username, password, port);
		
		File releaseDir = new File(releaseNumber);
		if (!releaseDir.exists()) {
			releaseDir.mkdir();
		}
		//Begin download directory
		DatabaseDumps.execute(dbAdaptor, releaseNumber, username, password, host, port, database);
		Biopax.execute(username, password, host, Integer.toString(port), database, releaseNumber);
		GSEAOutput.execute(username, password, host, port, database, releaseNumber);
		ReactomeBookGenerator.execute(username, password, host, port, database, releaseNumber);
		FetchTestReactomeOntologyFiles.execute(dbAdaptor, username, password, host, database, releaseNumber);
		CreateReleaseTarball.execute(releaseNumber);
		PathwaySummationMappingFile.execute(dbAdaptor, releaseNumber);
		MapOldStableIds.execute(username, password, host, releaseNumber);
		
		// These file copy commands now use absolute paths instead of relative ones
		String releaseDirAbsolute = "/usr/local/gkb/scripts/release";
		logger.info("Copying gene_association.reactome to release directory");
		Process copyGeneAssociationFile = Runtime.getRuntime().exec("cp " + releaseDirAbsolute + "/goa_prepare/gene_association.reactome " + releaseNumber);
		copyGeneAssociationFile.waitFor();
		logger.info("Copying models2pathways.tsv to release directory");
		Process copyModels2PathwaysFile = Runtime.getRuntime().exec("cp " + releaseDirAbsolute + "/biomodels/models2pathways.tsv " + releaseNumber);
		copyModels2PathwaysFile.waitFor();
		
		CreateReactome2BioSystems.execute(host, database, username, password, port, releaseNumber);
		
		// Move files to downloadDirectory release folder
		logger.info("Moving all generated files to ");
		String releaseDownloadDir = "/usr/local/gkb/scripts/release/download_directory/" + releaseNumber;
		logger.info("Moving all generated files to " + releaseDownloadDir);
		File folder = new File(releaseNumber);
		File[] releaseFiles = folder.listFiles();
		for (int i = 0; i < releaseFiles.length; i++) {
			if (releaseFiles[i].isDirectory() && releaseFiles[i].getName().equalsIgnoreCase("databases")) {
				Process removeDatabasesFolder = Runtime.getRuntime().exec("rm -r " + releaseDownloadDir + "/databases");
				removeDatabasesFolder.waitFor();
			}
			
			Files.move(Paths.get(releaseFiles[i].toString()), Paths.get(releaseDownloadDir + "/" + releaseFiles[i].getName()), StandardCopyOption.REPLACE_EXISTING); 
		}
		
		logger.info("Finished DownloadDirectory for release " + releaseNumber);
	}
}


