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
		
		Runtime.getRuntime().exec("mkdir -p " + releaseNumber);
		
		//Begin download directory
//		DatabaseDumps.execute(dbAdaptor, releaseNumber, username, password, host, port, database);
//		Biopax.execute(username, password, host, port, database, releaseNumber);
		GSEAOutput.execute(username, password, host, port, database);
		//TODO: runGSEAOutput
		//TODO: TheReactomeBookPDF
		//TODO: TheReactomeBookRTF
		//TODO: FetchEmptyProject
		//TODO: release_tarball
//		PathwaySummationMappingFile.execute(dbAdaptor);
//		MapOldStableIds.execute(username, password, releaseNumber);
		
		//TODO: Files Mover [gene_association.reactome, biomodels]
		
		//TODO: create_reactome2biosystems
	}
}
