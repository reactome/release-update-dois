package org.reactome.release.update_stable_ids;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.TransactionsNotSupportedException;

/**
 * This function iterates through all instances, and checks if it has been changed since the previous release.
 * Instances that have been changed have their 'identifierVersion' attribute incremented by 1 to reflect a change. 
 *
 */
public class Main 
{

    public static void main( String[] args ) throws Exception
    {
    	// This utility was meant to serve the same function as 'restore_database.pl'. 
    	// For now, we don't want the Java to do the DB archiving, so this won't be used. 
//       DatabaseUtils.archiveUsedDatabases("test_slice_67", "gk_central");
       
       String pathToConfig = "src/main/resources/config.properties";
       
       //Sets up the various DB Adaptors needed. This includes the current and previous test_slice versions on the release server, 
       //as well as gk_central on the curation server.
       Properties props = new Properties();
       props.load(new FileInputStream(pathToConfig));
       int port = Integer.valueOf(props.getProperty("port"));
       
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
       
       Long personId = Long.parseLong(props.getProperty("personInstanceId"));
       UpdateStableIds.stableIdUpdater(dbaSlice, dbaPrevSlice, dbaGkCentral, personId);
;    }
}
