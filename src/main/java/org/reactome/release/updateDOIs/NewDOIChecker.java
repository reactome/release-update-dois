package org.reactome.release.updateDOIs;

import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.util.GKApplicationUtilities;

public class NewDOIChecker {

  private MySQLAdaptor dbaTestReactome;
  private MySQLAdaptor dbaGkCentral;

  // Create adaptors for test_reactome and gk_central
  public void setTestReactomeAdaptor(MySQLAdaptor adaptor) {
    this.dbaTestReactome = adaptor;
  }

  public void setGkCentralAdaptor(MySQLAdaptor adaptor) {
    this.dbaGkCentral = adaptor;
  }

  public void findNewDOIs(GKInstance testReactomeIE, GKInstance gkCentralIE) {
    Collection<GKInstance> dois;
    Collection<GKInstance> gkdois;
    try {
      dois = this.dbaTestReactome.fetchInstanceByAttribute("Pathway", "doi", "NOT REGEXP", "^10.3180");
  // Line below is code used when testing this module; Allowed me to revert the doi attribute after modifying it. Make sure to uncomment other line below too.
      // dois = this.dbaTestReactome.fetchInstanceByAttribute("Pathway", "DB_ID", "REGEXP", "8939211|9006115|9018678|9033241");

      if (!dois.isEmpty()) {
        for (GKInstance doi : dois) {
          String stableIdFromDb = doi.getAttributeValue("stableIdentifier").toString();
          // This way of acquiring stableId is probably not ideal.
          String stableId[] = stableIdFromDb.split("] ");
          String updatedDoi = "10.3180/" + stableId[1];
      // Line below is again used for reverting DB changes.
          // String updatedDoi = "needs DOI";

          String dbId = doi.getAttributeValue("DB_ID").toString();
          doi.setAttributeValue("doi", updatedDoi);
          this.dbaTestReactome.updateInstanceAttribute(doi, "doi");

          // Grabs instance from gk central based on DB_ID taken from test_reactome, and updated doi field
          gkdois = this.dbaGkCentral.fetchInstanceByAttribute("Pathway", "DB_ID", "=", dbId);
          if (!gkdois.isEmpty()) {
            for (GKInstance gkdoi : gkdois) {
              gkdoi.setAttributeValue("doi", updatedDoi);
              this.dbaGkCentral.updateInstanceAttribute(gkdoi, "doi");
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
     * Create an InstanceEdit.
     * @param personID - ID of the associated Person entity.
     * @param creatorName - The name of the thing that is creating this InstanceEdit. Typically, you would want to use the package and classname that
     * uses <i>this</i> object, so it can be traced to the appropriate part of the program.
     * @return
     */
    public GKInstance createInstanceEdit(long personID, String creatorName) {
        GKInstance instanceEdit = null;
        try
        {
            instanceEdit = createDefaultIE(this.dbaTestReactome, personID, true, "Inserted by " + creatorName);
            instanceEdit.getDBID();
            this.dbaTestReactome.updateInstance(instanceEdit);
        }
        catch (Exception e)
        {
            // logger.error("Exception caught while trying to create an InstanceEdit: {}", e.getMessage());
            e.printStackTrace();
        }
        return instanceEdit;
    }

    // This code below was taken from 'add-links' repo: org.reactomeaddlinks.db.ReferenceCreator
    /**
     * Create and save in the database a default InstanceEdit associated with the Person entity whose DB_ID is <i>defaultPersonId</i>.
     * @param dba
     * @param defaultPersonId
     * @param needStore
     * @return an InstanceEdit object.
     * @throws Exception
     */
    public static GKInstance createDefaultIE(MySQLAdaptor dba, Long defaultPersonId, boolean needStore, String note) throws Exception {
        GKInstance defaultPerson = dba.fetchInstance(defaultPersonId);
        if (defaultPerson != null) {
            GKInstance newIE = NewDOIChecker.createDefaultInstanceEdit(defaultPerson);
            newIE.addAttributeValue(ReactomeJavaConstants.dateTime, GKApplicationUtilities.getDateTime());
            newIE.addAttributeValue(ReactomeJavaConstants.note, note);
            InstanceDisplayNameGenerator.setDisplayName(newIE);

            if (needStore) {
                dba.storeInstance(newIE);
            } else {
            // This 'else' block wasn't here when first copied from ReferenceCreator. Added to reduce future potential headaches. (JC)
              System.out.println("needStore set to false");
            }
            return newIE;
        } else {
            throw new Exception("Could not fetch Person entity with ID " + defaultPersonId + ". Please check that a Person entity exists in the database with this ID.");
        }
      }

      public static GKInstance createDefaultInstanceEdit(GKInstance person) {
        GKInstance instanceEdit = new GKInstance();
        PersistenceAdaptor adaptor = person.getDbAdaptor();
        instanceEdit.setDbAdaptor(adaptor);
        SchemaClass cls = adaptor.getSchema().getClassByName(ReactomeJavaConstants.InstanceEdit);
        instanceEdit.setSchemaClass(cls);

        try
        {
          instanceEdit.addAttributeValue(ReactomeJavaConstants.author, person);
        }
        catch (InvalidAttributeException | InvalidAttributeValueException e)
        {
          e.printStackTrace();
          // throw this back up the stack - no way to recover from in here.
          throw new Error(e);
        }

        return instanceEdit;
      }
}
