package org.reactome.release.updateDOIs;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.util.GKApplicationUtilities;

public class UpdateDOIs {

	private static final Logger logger = LogManager.getLogger();
	private static final Logger warningsLog = LogManager.getLogger("warningsLog");

	private static MySQLAdaptor dbaTestReactome;
	private static MySQLAdaptor dbaGkCentral;

	// Create adaptors for Test Reactome and GKCentral
	public static void setAdaptors(MySQLAdaptor adaptorTR, MySQLAdaptor adaptorGK) {
		dbaTestReactome = adaptorTR;
		dbaGkCentral = adaptorGK;
	}

	@SuppressWarnings("unchecked")
	public static void findAndUpdateDOIs(long authorIdTR, long authorIdGK, String pathToReport) {

		Collection<GKInstance> doisTR;
		Collection<GKInstance> doisGK;

		// Initialize instance edits for each DB
		String creatorFile = "org.reactome.release.updateDOIs.Main";
		GKInstance instanceEditTR = UpdateDOIs.createInstanceEdit(UpdateDOIs.dbaTestReactome, authorIdTR, creatorFile);
		GKInstance instanceEditGK = UpdateDOIs.createInstanceEdit(UpdateDOIs.dbaGkCentral, authorIdGK, creatorFile);
		// Gets the updated report file if it was provided for this release
		HashMap<String, HashMap<String,String>> expectedUpdatedDOIs = UpdateDOIs.getExpectedUpdatedDOIs(pathToReport);
		ArrayList<String> updated = new ArrayList<String>();
		ArrayList<String> notUpdated = new ArrayList<String>();
		int fetchHits = 0;
		try 
		{
			// Get all instances in Test Reactome in the Pathway table that don't have a 'doi' attribute starting with 10.3180, the Reactome DOI standard
			 doisTR = dbaTestReactome.fetchInstanceByAttribute("Pathway", "doi", "NOT REGEXP", "^10.3180");
			// Used during testing
//			doisTR = dbaTestReactome.fetchInstanceByAttribute("Pathway", "DB_ID", "REGEXP", "1912408|3232118|3232142|4085377|4090294|4655427|4755510|8985947|9013694|9034015");
			 // GKCentral should require transactional support
			if (dbaGkCentral.supportsTransactions())
			{
				if (!doisTR.isEmpty())
				{
					outerloop:
					for (GKInstance trDOI : doisTR)
					{
						// The dois are constructed from the instances 'stableIdentifier', which should be in the db already
						String stableIdFromDb = ((GKInstance) trDOI.getAttributeValue("stableIdentifier")).getDisplayName();
						String nameFromDb = trDOI.getAttributeValue("name").toString();
						String updatedDoi = "10.3180/" + stableIdFromDb;
						String dbId = trDOI.getAttributeValue("DB_ID").toString();

						// Used to verify that report contents are as expected, based on provided list from curators
						fetchHits++;
						if (expectedUpdatedDOIs.get(updatedDoi) != null && expectedUpdatedDOIs.get(updatedDoi).get("displayName").equals(nameFromDb))
						{	
							updated.add(updatedDoi);
						} else {
							String doiWithName = updatedDoi + ":" + nameFromDb;
							notUpdated.add(doiWithName);
							continue;
						}
						// This updates the 'modified' field for Pathways instances, keeping track of when changes happened for each instance
						// TODO: Put this after the update to GK Central so that we make sure values match
						trDOI.getAttributeValuesList("modified");
						trDOI.addAttributeValue("modified", instanceEditTR);
						trDOI.setAttributeValue("doi", updatedDoi);

						// Grabs instance from GKCentral based on DB_ID taken from Test Reactome and updates it's DOI
						dbaGkCentral.startTransaction();
						doisGK = dbaGkCentral.fetchInstanceByAttribute("Pathway", "DB_ID", "=", dbId);
						if (!doisGK.isEmpty())
						{
							for (GKInstance gkDOI : doisGK)
							{
								boolean verified = ReportTests.verifyDOIMatches(trDOI, gkDOI, updatedDoi);
								if (verified) 
								{
									gkDOI.getAttributeValuesList("modified");
									gkDOI.addAttributeValue("modified", instanceEditGK);
									gkDOI.setAttributeValue("doi", updatedDoi);
									dbaGkCentral.updateInstanceAttribute(gkDOI, "modified");
									dbaGkCentral.updateInstanceAttribute(gkDOI, "doi");
								} else {
									continue outerloop;
								}

								logger.info("Updated DOI: " + updatedDoi + " for " + nameFromDb);
							}
						} else {
							logger.error("Could not find attribute in gk_central");
						}
						dbaTestReactome.updateInstanceAttribute(trDOI, "modified");
						dbaTestReactome.updateInstanceAttribute(trDOI, "doi");
					}
					ReportTests.expectedUpdatesTests(expectedUpdatedDOIs, updated, notUpdated, fetchHits );
				} else {
					logger.info("No DOIs to update");
				}
				dbaGkCentral.commit();
			} else {
				logger.fatal("Unable to open transaction with GK Central, rolling back");
				dbaGkCentral.rollback();
			}
		} catch (Exception e) {
			try
			{
				dbaGkCentral.rollback();
			} catch (Exception err) {
				e.printStackTrace();
			}
			e.printStackTrace();
		}
	}

	// Parses input report and places each line's contents in HashMap
	public static HashMap<String, HashMap<String,String>> getExpectedUpdatedDOIs(String pathToReport) {

		HashMap<String, HashMap<String, String>> expectedUpdatedDOIs = new HashMap<String, HashMap<String,String>>();
		try 
		{
			FileReader fr = new FileReader(pathToReport);
			BufferedReader br = new BufferedReader(fr);

			String sCurrentLine;
			while ((sCurrentLine = br.readLine()) != null) 
			{
				HashMap<String, String> doiAttributes = new HashMap<String,String>();
				String[] commaSplit = sCurrentLine.split(",");
				String reactomeDoi = commaSplit[0];
				String displayName = commaSplit[1];
				int lastPeriodIndex = commaSplit[0].lastIndexOf(".");
				String[] versionSplit = {reactomeDoi.substring(0, lastPeriodIndex), reactomeDoi.substring(lastPeriodIndex+1)};
				String stableId = versionSplit[0].replace("10.3180/", "");
				String stableIdVersion = versionSplit[1];
				doiAttributes.put("displayName", displayName);
				doiAttributes.put("stableId", stableId);
				doiAttributes.put("stableIdVersion", stableIdVersion);
				expectedUpdatedDOIs.put(reactomeDoi, doiAttributes);
			}
			br.close();
			fr.close();

		} catch (Exception e) {
			warningsLog.warn("No input file found -- Continuing without checking DOIs");
			e.printStackTrace();
		}
		return expectedUpdatedDOIs;
	}

	/**
	 * Create an InstanceEdit.
	 * 
	 * @param personID
	 *            - ID of the associated Person entity.
	 * @param creatorName
	 *            - The name of the thing that is creating this InstanceEdit.
	 *            Typically, you would want to use the package and classname that
	 *            uses <i>this</i> object, so it can be traced to the appropriate
	 *            part of the program.
	 * @return
	 */
	public static GKInstance createInstanceEdit(MySQLAdaptor dbAdaptor, long personID, String creatorName) {
		GKInstance instanceEdit = null;
		try {
			instanceEdit = createDefaultIE(dbAdaptor, personID, true, "Inserted by " + creatorName);
			instanceEdit.getDBID();
			dbAdaptor.updateInstance(instanceEdit);
		} catch (Exception e) {
			// logger.error("Exception caught while trying to create an InstanceEdit: {}",
			// e.getMessage());
			e.printStackTrace();
		}
		return instanceEdit;
	}

	// This code below was taken from 'add-links' repo:
	// org.reactomeaddlinks.db.ReferenceCreator
	/**
	 * Create and save in the database a default InstanceEdit associated with the
	 * Person entity whose DB_ID is <i>defaultPersonId</i>.
	 * 
	 * @param dba
	 * @param defaultPersonId
	 * @param needStore
	 * @return an InstanceEdit object.
	 * @throws Exception
	 */
	public static GKInstance createDefaultIE(MySQLAdaptor dba, Long defaultPersonId, boolean needStore, String note)
			throws Exception {
		GKInstance defaultPerson = dba.fetchInstance(defaultPersonId);
		if (defaultPerson != null) {
			GKInstance newIE = UpdateDOIs.createDefaultInstanceEdit(defaultPerson);
			newIE.addAttributeValue(ReactomeJavaConstants.dateTime, GKApplicationUtilities.getDateTime());
			newIE.addAttributeValue(ReactomeJavaConstants.note, note);
			InstanceDisplayNameGenerator.setDisplayName(newIE);

			if (needStore) {
				dba.storeInstance(newIE);
			} else {
				// This 'else' block wasn't here when first copied from ReferenceCreator. Added
				// to reduce future potential headaches. (JC)
				System.out.println("needStore set to false");
			}
			return newIE;
		} else {
			throw new Exception("Could not fetch Person entity with ID " + defaultPersonId
					+ ". Please check that a Person entity exists in the database with this ID.");
		}
	}

	public static GKInstance createDefaultInstanceEdit(GKInstance person) {
		GKInstance instanceEdit = new GKInstance();
		PersistenceAdaptor adaptor = person.getDbAdaptor();
		instanceEdit.setDbAdaptor(adaptor);
		SchemaClass cls = adaptor.getSchema().getClassByName(ReactomeJavaConstants.InstanceEdit);
		instanceEdit.setSchemaClass(cls);

		try {
			instanceEdit.addAttributeValue(ReactomeJavaConstants.author, person);
		} catch (InvalidAttributeException | InvalidAttributeValueException e) {
			e.printStackTrace();
			// throw this back up the stack - no way to recover from in here.
			throw new Error(e);
		}

		return instanceEdit;
	}
}
