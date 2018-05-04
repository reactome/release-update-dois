package org.reactome.release.updateDOIs;

import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.util.Properties;

import org.gk.persistence.MySQLAdaptor;

public class UpdateDOIs {

  final static Logger logger = Logger.getLogger(UpdateDOIs.class);

  public static void main( String[] args ) {

    String pathToConfig = "src/main/resources/config.properties";
    
    if (args.length > 0 && !args[0].equals("")) {
      pathToConfig = args[0];
    }

    UpdateDOIs.executeUpdateDOIs(pathToConfig);
  }

  public static void executeUpdateDOIs(String pathToResources) {

    MySQLAdaptor testReactomeDBA = null;
    MySQLAdaptor gkCentralDBA = null;
    long authorId = 0;

    try {
      Properties props = new Properties();
      props.load(new FileInputStream(pathToResources));

      String user = props.getProperty("user");
      String password = props.getProperty("password");
      String host = props.getProperty("host");
      String databaseTR = props.getProperty("databaseTR");
      String databaseGk = props.getProperty("databaseGK");
      int port = Integer.valueOf(props.getProperty("port"));
      authorId = Integer.valueOf(props.getProperty("authorId"));

      // Set up db connections.
      testReactomeDBA = new MySQLAdaptor(host, databaseTR, user, password, port);
      gkCentralDBA = new MySQLAdaptor(host, databaseGk, user, password, port);
    } catch (Exception e) {
      e.printStackTrace();
    }

      findNewDOIsAndUpdate findNewDOIsAndUpdate = new findNewDOIsAndUpdate();
      findNewDOIsAndUpdate.setTestReactomeAdaptor(testReactomeDBA);
      findNewDOIsAndUpdate.setGkCentralAdaptor(gkCentralDBA);

      findNewDOIsAndUpdate.findNewDOIsAndUpdate(authorId);
      
      logger.info( "UpdateDOIs Complete" );
    }

}
