package org.reactome.release.updateDOIs;

import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.util.Properties;

import org.gk.persistence.MySQLAdaptor;

public class UpdateDOIs {

  final static Logger logger = Logger.getLogger(UpdateDOIs.class);

  public static void main( String[] args ) {

    String pathToConfig = "src/main/resources/config.properties";
    String pathToReport = "src/main/resources/UpdateDOIs.report";
    if (args.length > 0 && !args[0].equals("")) {
      pathToConfig = args[0];
      pathToReport = args[1];
    }

    UpdateDOIs.executeUpdateDOIs(pathToConfig, pathToReport);
  }

  public static void executeUpdateDOIs(String pathToResources, String pathToReport) {

    MySQLAdaptor testReactomeDBA = null;
    MySQLAdaptor gkCentralDBA = null;
    long authorIdTR = 0;
    long authorIdGK = 0;

    try {
      Properties props = new Properties();
      props.load(new FileInputStream(pathToResources));

      String user = props.getProperty("user");
      String password = props.getProperty("password");
      String hostTR = props.getProperty("hostTR");
      String hostGK = props.getProperty("hostGK");
      String databaseTR = props.getProperty("databaseTR");
      String databaseGK = props.getProperty("databaseGK");
      int port = Integer.valueOf(props.getProperty("port"));
      authorIdTR = Integer.valueOf(props.getProperty("authorIdTR"));
      authorIdGK = Integer.valueOf(props.getProperty("authorIdGK"));


      // Set up db connections.
      testReactomeDBA = new MySQLAdaptor(hostTR, databaseTR, user, password, port);
      gkCentralDBA = new MySQLAdaptor(hostGK, databaseGk, user, password, port);
    } catch (Exception e) {
      e.printStackTrace();
    }

      findNewDOIsAndUpdate findNewDOIsAndUpdate = new findNewDOIsAndUpdate();
      findNewDOIsAndUpdate.setTestReactomeAdaptor(testReactomeDBA);
      findNewDOIsAndUpdate.setGkCentralAdaptor(gkCentralDBA);
      findNewDOIsAndUpdate.findAndUpdateDOIs(authorIdTR, authorIdGK, pathToReport);

      logger.info( "UpdateDOIs Complete" );
    }

}
