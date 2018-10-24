package org.reactome.release.downloadDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.gk.persistence.MySQLAdaptor;

public class Main {
	static MySQLAdaptor dbAdaptor = null;
	
	public static void main(String[] args) throws Exception {
		
		String pathToConfig = "src/main/resources/config.properties";
		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));
		
		//TODO: Check stable identifiers db exists; 
		//TODO: Archive previous version download directory
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
		int releaseNumber = Integer.valueOf(props.getProperty("release"));
		dbAdaptor = new MySQLAdaptor(host, database, username, password, port);
		
		Runtime.getRuntime().exec("mkdir -p " + releaseNumber);
		
		//Begin download directory
		DatabaseDumps.execute(dbAdaptor, releaseNumber, username, password, host, port, database);
		Biopax.execute(username, password, host, Integer.toString(port), database, Integer.toString(releaseNumber));
		GSEAOutput.execute(username, password, host, port, database, releaseNumber);
		ReactomeBookGenerator.execute(username, password, host, port, database, releaseNumber);
		FetchTestReactomeOntologyFiles.execute(dbAdaptor, username, password, host, releaseNumber);
		CreateReleaseTarball.execute(releaseNumber);
		PathwaySummationMappingFile.execute(dbAdaptor, releaseNumber);
		MapOldStableIds.execute(username, password, host, releaseNumber);
		
		// These file copy commands now use absolute paths instead of relative ones
		String releaseDirAbsolute = "/usr/local/gkb/scripts/release";
		Process copyGeneAssociationFile = Runtime.getRuntime().exec("cp " + releaseDirAbsolute + "/goa_prepare/gene_association.reactome " + releaseNumber);
		copyGeneAssociationFile.waitFor();
		Process copyModels2PathwaysFile = Runtime.getRuntime().exec("cp " + releaseDirAbsolute + "/biomodels/models2pathways.tsv " + releaseNumber);
		copyModels2PathwaysFile.waitFor();
		
		CreateReactome2BioSystems.execute(host, database, username, password, port, releaseNumber);
		
		// Move files to downloadDirectory release folder
		String releaseDownloadDir = "/usr/local/gkb/scripts/release/download_directory/" + releaseNumber;
		File folder = new File(Integer.toString(releaseNumber));
		File[] releaseFiles = folder.listFiles();
		for (int i = 0; i < releaseFiles.length; i++) {
			Process moveFileToDownloadDir = Runtime.getRuntime().exec("mv " + releaseFiles[i] + " " + releaseDownloadDir);
			moveFileToDownloadDir.waitFor();
		}
	}
}


