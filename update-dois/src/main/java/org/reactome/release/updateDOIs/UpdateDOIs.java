package org.reactome.release.updateDOIs;

import java.io.IOException;
import java.util.Properties;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.model.PersistenceAdaptor;

public class UpdateDOIs {

  public static void main( String[] args ) {

    String pathToResources = "src/main/resources/config.properties";

    if (args.length > 0 && !args[0].equals("")) {
      pathToResources = args[0];
    }

    UpdateDOIs.executeUpdateDOIs(pathToResources);
  }

  public static void executeUpdateDOIs(String pathToResources) {

    MySQLAdaptor testReactomeDBA = null;
    MySQLAdaptor gkCentralDBA = null;

    try {
      String user = "root";
      String password = "root";
      String host = "localhost";
      String databaseTest = "test_reactome_64";
      String databaseGk = "gk_central";
      int port = 3306;
      // Set up db connections.
      testReactomeDBA = new MySQLAdaptor(host, databaseTest, user, password, port);
      gkCentralDBA = new MySQLAdaptor(host, databaseGk, user, password, port);
    } catch (Exception e) {
      e.printStackTrace();
    }
      NewDOIChecker newDOIChecker = new NewDOIChecker();
      newDOIChecker.setTestReactomeAdaptor(testReactomeDBA);
      newDOIChecker.setGkCentralAdaptor(gkCentralDBA);
  //// Insert your own author id!
      // long authorId = <Your id>;
      String creatorFile = "org.reactome.release.updateDOIs.UpdateDOIs";
      GKInstance instanceEditTestReactome = newDOIChecker.createInstanceEdit(authorId, creatorFile);
      GKInstance instanceEditGkCentral = newDOIChecker.createInstanceEdit(authorId, creatorFile);
      newDOIChecker.findNewDOIs(instanceEditTestReactome, instanceEditGkCentral);
    // Useful to report information back, such as number of changes?
      System.out.println( "UpdateDOIs Complete" );
    }

}
