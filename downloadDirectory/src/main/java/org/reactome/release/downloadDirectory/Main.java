package org.reactome.release.downloadDirectory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import org.gk.persistence.MySQLAdaptor;

public class Main {
	static MySQLAdaptor dbAdaptor = null;
	
	public static void main(String[] args) throws Exception {
		
		String pathToConfig = "src/main/resources/config.properties";
		Properties props = new Properties();
		props.load(new FileInputStream(pathToConfig));
		int releaseNumber = 65;
		
		//Set up DB adaptor
		Object speciesToInferFromLong = "Homo sapiens";
		String username = props.getProperty("username");
		String password = props.getProperty("password");
		String database = props.getProperty("database");
		String host = props.getProperty("host");
		int port = Integer.valueOf(props.getProperty("port"));
		dbAdaptor = new MySQLAdaptor(host, database, username, password, port);
		
		//Begin download directory
//		DatabaseDumps.execute(dbAdaptor);
		//SBMLDumper.execute(dbAdaptor);
		//TODO: runDiagramDumper
//		OutputCuratedComplexInstances.execute(dbAdaptor);
		//TODO: run_biopax
		//TODO: runGSEAOutput
		//TODO: TheReactomeBookPDF
		//TODO: TheReactomeBookRTF
		//TODO: FetchEmptyProject
		//TODO: release_tarball
		PathwaySummationMappingFile.execute(dbAdaptor);
		//TODO: StableIdToUniprotAccessionMappingFile
		//TODO: reactome_stable_ids_mapping
		
		//TODO: Files Mover [gene_association.reactome, biomodels]
		
		//TODO: create_reactome2biosystems
	}
}
