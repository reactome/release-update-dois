package org.reactome.release.updateDOIs;

import java.io.IOException;
import java.util.Properties;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.model.PersistenceAdaptor;

public class UpdateDOIs {

  public static void main( String[] args ) {

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
      // long authorId = <YourOwnId>;
      GKInstance instanceEdit = newDOIChecker.createInstanceEdit(authorId, "org.reactome.release.updateDOIs.UpdateDOIs");
      newDOIChecker.findNewDOIs(instanceEdit);
    // Useful to report information back, such as number of changes?
      System.out.println( "UpdateDOIs Complete" );
    }

}
