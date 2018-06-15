package org.reactome.release.common;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

public abstract class ReleaseStep
{
	protected static final Logger logger = LogManager.getLogger();
	
	protected boolean testMode;
	
	protected static MySQLAdaptor getMySQLAdaptorFromProperties(Properties props) throws IOException, FileNotFoundException, SQLException
	{
		
		String dbHost = props.getProperty("db.host", "localhost");
		String dbUser = props.getProperty("db.user");
		String dbPassword = props.getProperty("db.password");
		String dbName = props.getProperty("db.name");
		int dbPort = new Integer(props.getProperty("db.port", "3306"));
		
		MySQLAdaptor adaptor = new MySQLAdaptor(dbHost, dbName, dbUser, dbPassword, dbPort);
		return adaptor;
	}
	
	protected void loadTestModeFromProperties(Properties props)
	{
		this.testMode = new Boolean(props.getProperty("testMode", "true"));
		if (!testMode)
		{
			logger.info("Test mode is OFF - database will be updated!");
		}
		else
		{
			logger.info("Test mode is ON - no database changes will be made.");
		}
	}
	
	abstract public void executeStep(Properties props) throws Exception; 
}
