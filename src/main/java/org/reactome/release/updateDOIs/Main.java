package org.reactome.release.updateDOIs;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

public class Main {

	private static final Logger logger = LogManager.getLogger();
	private static final String RESOURCES_DIR = Paths.get("src", "main", "resources").toString();

  public static void main( String[] args ) throws IOException, SQLException {

	 // Default locations of properties and pre-set report files
	 // Will override if arguments are provided
    String pathToConfig = args.length > 0 && !args[0].isEmpty() ? args[0] : Paths.get(RESOURCES_DIR,"config.properties").toString();

    String pathToReport = Paths.get(RESOURCES_DIR,"UpdateDOIs.report").toString();
    boolean testMode = true;
    if (args.length > 1 && !args[1].isEmpty()) {
      pathToReport = args[1];
      testMode = false;
    }

    MySQLAdaptor dbaTestReactome = null;
    MySQLAdaptor dbaGkCentral = null;
    long personId = 0;

    // Properties file should contain information needed to access current Test Reactome and GKCentral databases
    String releaseNumber = "";
    try 
    {
      Properties props = new Properties();
      props.load(new FileInputStream(pathToConfig));

      String userTR = props.getProperty("release.database.user");
      String userGK = props.getProperty("curator.database.user");
      String passwordTR = props.getProperty("release.database.password");
      String passwordGK = props.getProperty("curator.database.password");
      String hostTR = props.getProperty("release.database.host");
      String hostGK = props.getProperty("curator.database.host");
      String databaseTR = props.getProperty("release_current.name");
      String databaseGK = props.getProperty("curator.database.name");
      personId = Integer.valueOf(props.getProperty("personId"));
      int portTR = Integer.valueOf(props.getProperty("release.database.port"));
      int portGK = Integer.valueOf(props.getProperty("curator.database.port"));
      releaseNumber = props.getProperty("releaseNumber");
      //if (props.getProperty("testMode") != null) {
      //  testMode = Boolean.valueOf(props.getProperty("testMode"));
      //}

      // Set up db connections.
      dbaTestReactome = new MySQLAdaptor(hostTR, databaseTR, userTR, passwordTR, portTR);
      dbaGkCentral = new MySQLAdaptor(hostGK, databaseGK, userGK, passwordGK, portGK);
    } catch (SQLException e) {
      throw new SQLException("Unable to create MySQL adaptor. Check properties file.");
    }
      UpdateDOIs.setAdaptors(dbaTestReactome, dbaGkCentral);
      logger.info("Starting UpdateDOIs");
      UpdateDOIs.findAndUpdateDOIs(personId, pathToReport, releaseNumber, testMode);
      if (!testMode) {
        logger.info("UpdateDOIs Complete");
      } else {
        logger.info("Finished test run of UpdateDOIs");
      }
    }
}
