package org.reactome.release.chebiupdate;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import org.gk.persistence.MySQLAdaptor;

public class Main
{

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
		MySQLAdaptor adaptor = getMySQLAdaptorFromProperties(props);
		boolean testMode = new Boolean(props.getProperty("testMode", "true"));
		
		ChebiUpdater chebiUpdater = new ChebiUpdater(adaptor, testMode);
		
		chebiUpdater.updateChebiReferenceMolecules();
		chebiUpdater.checkForDuplicates();
	}

	private static MySQLAdaptor getMySQLAdaptorFromProperties(Properties props) throws IOException, FileNotFoundException, SQLException
	{
		
		String dbHost = props.getProperty("db.host", "localhost");
		String dbUser = props.getProperty("db.user");
		String dbPassword = props.getProperty("db.password");
		String dbName = props.getProperty("db.name");
		int dbPort = new Integer(props.getProperty("db.port", "3306"));
		MySQLAdaptor adaptor = new MySQLAdaptor(dbHost, dbName, dbUser, dbPassword, dbPort);
		return adaptor;
	}
}
