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
		
		//Set up DB adaptor
		Object speciesToInferFromLong = "Homo sapiens";
		String username = props.getProperty("username");
		String password = props.getProperty("password");
		String database = props.getProperty("database");
		String host = props.getProperty("host");
		int port = Integer.valueOf(props.getProperty("port"));
		int releaseNumber = Integer.valueOf(props.getProperty("release"));
		dbAdaptor = new MySQLAdaptor(host, database, username, password, port);
		
//		Runtime.getRuntime().exec("mkdir -p " + releaseNumber);
		
		//Begin download directory
//		DatabaseDumps.execute(dbAdaptor, releaseNumber, username, password, host, port, database);
//		Biopax.execute(username, password, host, port, database, releaseNumber);
//		GSEAOutput.execute(username, password, host, port, database);
		//TODO: TheReactomeBookPDF
		//TODO: TheReactomeBookRTF
//		FetchTestReactomeOntologyFiles.execute(dbAdaptor, username, password);
//		CreateReleaseTarball.execute(releaseNumber);
//		PathwaySummationMappingFile.execute(dbAdaptor);
//		MapOldStableIds.execute(username, password, releaseNumber);
		
		// These file copy commands now use absolute paths instead of relative ones
		String releaseDirAbsolute = "/usr/local/gkb/scripts/release";
		Process copyGeneAssociationFile = Runtime.getRuntime().exec("cp " + releaseDirAbsolute + "/goa_prepare/gene_association.reactome .");
		copyGeneAssociationFile.waitFor();
		Process copyModels2PathwaysFile = Runtime.getRuntime().exec("cp " + releaseDirAbsolute + "/biomodels/models2pathways.tsv .");
		copyModels2PathwaysFile.waitFor();
		
		//TODO: create_reactome2biosystems
	}
}
