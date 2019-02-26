package org.reactome.release.updateStableIds;

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

/**
 * This function iterates through all instances, and checks if it has been changed since the previous release.
 * Instances that have been changed have their 'identifierVersion' attribute incremented by 1 to reflect a change. 
 *
 */
public class Main 
{
	private static final Logger logger = LogManager.getLogger();
	
    public static void main( String[] args ) throws Exception
    {
    	logger.info("Beginning UpdateStableIds step...");
       
    	String pathToConfig = "";
    	if (args.length > 0) {
    		pathToConfig = args[0];
    	} else {
    		pathToConfig = "src/main/resources/config.properties";
    	}
       
       //Sets up the various DB Adaptors needed. This includes the current and previous test_slice versions on the release server, 
       //as well as gk_central on the curation server.
       Properties props = new Properties();
       props.load(new FileInputStream(pathToConfig));
       int port = Integer.valueOf(props.getProperty("port"));
       int releaseNumber = Integer.valueOf(props.getProperty("releaseNumber"));
       int prevReleaseNumber = releaseNumber - 1;
       
       logger.info("Creating DB adaptors for test_slice_" + releaseNumber + ", test_slice_" + prevReleaseNumber + "_snapshot and gk_central");
       
       String sliceUsername = props.getProperty("sliceUsername");
       String slicePassword = props.getProperty("slicePassword");
       String sliceHost = props.getProperty("sliceHost");	   
       String sliceDatabase = props.getProperty("sliceDatabase");
       MySQLAdaptor dbaSlice = new MySQLAdaptor(sliceHost, sliceDatabase, sliceUsername, slicePassword, port);
       
       String prevSliceDatabase = props.getProperty("prevSliceDatabase");
       MySQLAdaptor dbaPrevSlice = new MySQLAdaptor(sliceHost, prevSliceDatabase, sliceUsername, slicePassword, port);
       
       String gkCentralUsername = props.getProperty("gkCentralUsername");
       String gkCentralPassword = props.getProperty("gkCentralPassword");
       String gkCentralHost = props.getProperty("gkCentralHost");
       String gkCentralDatabase = props.getProperty("gkCentralDatabase");
       
       MySQLAdaptor dbaGkCentral = new MySQLAdaptor(gkCentralHost, gkCentralDatabase, gkCentralUsername, gkCentralPassword, port);
       
       long personId = Long.parseLong(props.getProperty("personInstanceId"));
       StableIdentifierUpdater.updateStableIdentifiers(dbaSlice, dbaPrevSlice, dbaGkCentral, personId);
       logger.info("Finished UpdateStableIds step");
;    }
}
