package org.reactome.release.updateDOIs;

//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.persistence.MySQLAdaptor;

public class Main {

//  final static Logger logger = Logger.getLogger(UpdateDOIs.class);
	private static final Logger logger = LogManager.getLogger();

  public static void main( String[] args ) {

	 // Default locations of properties and pre-set report files
	 // Will override if arguments are provided
	 // TODO: Lots of room for error here if only 1 argument provided, or if the report file isn't updated
    String pathToConfig = "src/main/resources/config.properties";
    String pathToReport = "src/main/resources/UpdateDOIs.report";
    if (args.length > 0 && !args[0].equals(""))
    {
      pathToConfig = args[0];
      pathToReport = args[1];
    }

    MySQLAdaptor dbaTestReactome = null;
    MySQLAdaptor dbaGkCentral = null;
    long authorIdTR = 0;
    long authorIdGK = 0;

    // Properties file should contain information needed to access current Test Reactome and GKCentral databases
    try 
    {
      Properties props = new Properties();
      props.load(new FileInputStream(pathToConfig));

      String userTR = props.getProperty("userTR");
      String userGK = props.getProperty("userGK");
      String passwordTR = props.getProperty("passwordTR");
      String passwordGK = props.getProperty("passwordGK");
      String hostTR = props.getProperty("hostTR");
      String hostGK = props.getProperty("hostGK");
      String databaseTR = props.getProperty("databaseTR");
      String databaseGK = props.getProperty("databaseGK");
      authorIdTR = Integer.valueOf(props.getProperty("authorIdTR"));
      authorIdGK = Integer.valueOf(props.getProperty("authorIdGK"));
      int port = Integer.valueOf(props.getProperty("port"));

      // Set up db connections.
      dbaTestReactome = new MySQLAdaptor(hostTR, databaseTR, userTR, passwordTR, port);
      dbaGkCentral = new MySQLAdaptor(hostGK, databaseGK, userGK, passwordGK, port);
    } catch (Exception e) {
      e.printStackTrace();
    }
      UpdateDOIs.setAdaptors(dbaTestReactome, dbaGkCentral);
      UpdateDOIs.findAndUpdateDOIs(authorIdTR, authorIdGK, pathToReport);

      logger.info( "UpdateDOIs Complete" );
    }
}
