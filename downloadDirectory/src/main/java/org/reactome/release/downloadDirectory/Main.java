package org.reactome.release.downloadDirectory;

import java.io.FileInputStream;
import java.util.Properties;

import org.gk.persistence.MySQLAdaptor;

public class Main {
	static MySQLAdaptor dbAdaptor = null;
	
	public static void main(String[] args) throws Exception {
		
		String pathToConfig = "src/main/resources/config.properties";
		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));
		
		//TODO: Check stable identifiers db exists; 
		//TODO: Describe each functions outputs
		//Set up DB adaptor
		Object speciesToInferFromLong = "Homo sapiens";
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
		GSEAOutput.execute(username, password, host, port, database, releaseNumber);
//		//TODO: TheReactomeBookPDF
//		//TODO: TheReactomeBookRTF
		FetchTestReactomeOntologyFiles.execute(dbAdaptor, username, password, releaseNumber);
		CreateReleaseTarball.execute(releaseNumber);
		PathwaySummationMappingFile.execute(dbAdaptor, releaseNumber);
		MapOldStableIds.execute(username, password, releaseNumber);
		
		// These file copy commands now use absolute paths instead of relative ones
		String releaseDirAbsolute = "/usr/local/gkb/scripts/release";
		Process copyGeneAssociationFile = Runtime.getRuntime().exec("cp " + releaseDirAbsolute + "/goa_prepare/gene_association.reactome " + releaseNumber);
		copyGeneAssociationFile.waitFor();
		Process copyModels2PathwaysFile = Runtime.getRuntime().exec("cp " + releaseDirAbsolute + "/biomodels/models2pathways.tsv " + releaseNumber);
		copyModels2PathwaysFile.waitFor();
		
		CreateReactome2BioSystems.execute(host, database, username, password, port, releaseNumber);
		
		Biopax.execute(username, password, host, Integer.toString(port), database, Integer.toString(releaseNumber));
	}
}


