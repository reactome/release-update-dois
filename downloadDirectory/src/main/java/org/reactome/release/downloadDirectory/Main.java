package org.reactome.release.downloadDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
		String pathToSpeciesConfig = "src/main/resources/Species.json";
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
		if (!releaseDir.exists()) {
			releaseDir.mkdir();
		}
		//Begin download directory
//		DatabaseDumps.execute(dbAdaptor, releaseNumber, username, password, host, port, database);
		Biopax.execute(username, password, host, Integer.toString(port), database, releaseNumber, pathToSpeciesConfig);
		GSEAOutput.execute(dbAdaptor, releaseNumber);
		ReactomeBookGenerator.execute(username, password, host, port, database, releaseNumber, releaseDownloadDir);
		FetchTestReactomeOntologyFiles.execute(dbAdaptor, username, password, host, database, releaseNumber);
		CreateReleaseTarball.execute(releaseNumber, releaseDownloadDir);
		PathwaySummationMappingFile.execute(dbAdaptor, releaseNumber);
		MapOldStableIds.execute(username, password, host, releaseNumber);
		
		// These file copy commands now use absolute paths instead of relative ones
		logger.info("Copying gene_association.reactome to release directory");
		Files.copy(Paths.get(releaseDirAbsolute + "goa_prepare/gene_association.reactome"), Paths.get(releaseNumber + "gene_association.reactome"), StandardCopyOption.REPLACE_EXISTING);
		logger.info("Copying models2pathways.tsv to release directory");
		Files.copy(Paths.get(releaseDirAbsolute + "biomodels/models2pathways.tsv"), Paths.get(releaseNumber + "models2pathways.tsv"), StandardCopyOption.REPLACE_EXISTING);
		
		CreateReactome2BioSystems.execute(host, database, username, password, port, releaseNumber);
		// Move files to downloadDirectory release folder
		logger.info("Moving all generated files to " + releaseDownloadDirWithNumber);
		File folder = new File(releaseNumber);
		File[] releaseFiles = folder.listFiles();
		for (int i = 0; i < releaseFiles.length; i++) {
			System.out.println(releaseFiles[i]);
			if (releaseFiles[i].isDirectory() && releaseFiles[i].getName().equalsIgnoreCase("databases")) {
				FileUtils.deleteDirectory(new File(releaseDownloadDirWithNumber + "/databases"));
			}
			
			Files.move(Paths.get(releaseFiles[i].toString()), Paths.get(releaseDownloadDirWithNumber + "/" + releaseFiles[i].getName()), StandardCopyOption.REPLACE_EXISTING); 
		}
		
		logger.info("Finished DownloadDirectory for release " + releaseNumber);
	}
}


