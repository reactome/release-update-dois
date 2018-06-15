package org.reactome.release.chebiupdate;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

public class Main
{
//	private static final Logger logger = LogManager.getLogger("ChEBIUpdateLogger");
	
	public static void main(String[] args) throws SQLException, Exception
	{
		// Assume the path the the properties file is ./chebi-update.properties
		// but if args[] is not empty, then the first argument must be the path to
		// the resources file.
		String pathToResources = "./chebi-update.properties";
		if (args.length > 0)
		{
			pathToResources = args[0];
		}
		
		Properties props = new Properties();
		props.load(new FileInputStream(pathToResources));

		ChebiUpdateStep chebiUpdateStep = new ChebiUpdateStep();
		chebiUpdateStep.executeStep(props);
	}
}
