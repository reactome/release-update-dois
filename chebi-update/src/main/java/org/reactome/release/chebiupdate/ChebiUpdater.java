package org.reactome.release.chebiupdate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.PersistenceAdaptor;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.persistence.MySQLAdaptor.AttributeQueryRequest;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;
import org.gk.util.GKApplicationUtilities;

import uk.ac.ebi.chebi.webapps.chebiWS.client.ChebiWebServiceClient;
import uk.ac.ebi.chebi.webapps.chebiWS.model.ChebiWebServiceFault_Exception;
import uk.ac.ebi.chebi.webapps.chebiWS.model.DataItem;
import uk.ac.ebi.chebi.webapps.chebiWS.model.Entity;


public class ChebiUpdater {
	private static final Logger logger = LogManager.getLogger("ChEBIUpdateLogger");
	private boolean testMode = true;
	private MySQLAdaptor adaptor;
	private ChebiWebServiceClient chebiClient = new ChebiWebServiceClient();
	private StringBuilder identifierSB = new StringBuilder();
	private StringBuilder formulaUpdateSB = new StringBuilder();
	private StringBuilder formulaFillSB = new StringBuilder();
	private StringBuilder nameSB = new StringBuilder();
	private StringBuilder duplicatesSB = new StringBuilder();
	private StringBuilder refEntChanges = new StringBuilder();
	private long personID;
	
	public ChebiUpdater(MySQLAdaptor adaptor, boolean testMode, long personID)
	{
		this.adaptor = adaptor;
		this.testMode = testMode;
		this.personID = personID;
	}

	/**
	 * Update ChEBI ReferenceMolecules. This method will query ChEBI for up-to-date
	 * information, and using that information it will: <br/>
	 * <ul>
	 * <li>Update the names of ReferenceEntitites that are refer to the
	 * ReferenceMolecule</li>
	 * <li>Update the names of ReferenceMolecules</li>
	 * <li>Update the identifiers of ReferenceMolecules</li>
	 * <li>Update the formulae of ReferenceMolecules</li>
	 * </ul>
	 * 
	 * @throws SQLException
	 * @throws Exception
	 */
	public void updateChebiReferenceMolecules() throws SQLException, Exception
	{
		@SuppressWarnings("unchecked")
		String chebiRefDBID = (new ArrayList<GKInstance>( adaptor.fetchInstanceByAttribute("ReferenceDatabase", "name", "=", "ChEBI"))).get(0).getDBID().toString();

		@SuppressWarnings("unchecked")
		Collection<GKInstance> refMolecules = (Collection<GKInstance>) adaptor.fetchInstanceByAttribute("ReferenceMolecule", "referenceDatabase", "=", chebiRefDBID);

		// A map: key is the DB_ID of a ReferneceMolecule, value is the
		// uk.ac.ebi.chebi.webapps.chebiWS.model.Entity from ChEBI.
		Map<Long, Entity> entityMap = Collections.synchronizedMap(new HashMap<Long, Entity>());
		// A list of the ReferenceMolecules where we could nto get info from ChEBI.
		List<GKInstance> failedEntitiesList = Collections.synchronizedList(new ArrayList<GKInstance>());

		logger.info("{} ChEBI ReferenceMolecules to check...", refMolecules.size());

		retrieveUpdatesFromChebi(refMolecules, entityMap, failedEntitiesList);

		logger.info("Number of entities we were able to retrieve information about: {}", entityMap.size());
		logger.info("Number of entities we were NOT able to retrieve information about: {}", failedEntitiesList.size());

		for (GKInstance molecule : failedEntitiesList)
		{
			logger.info("Could not get info from ChEBI for: {}", molecule.toString());
		}
		
		GKInstance instanceEdit = null;
		if (!testMode)
		{
			instanceEdit = createInstanceEdit(this.adaptor, this.personID, this.getClass().getCanonicalName());
		}

		for (Long moleculeDBID : entityMap.keySet())
		{
			// One transaction per molecule - is this too many? If this runs too slow, maybe switch to one transaction per
			// program execution.
			if (!testMode && adaptor.supportsTransactions())
			{
				adaptor.startTransaction();
			}
			
			GKInstance molecule = adaptor.fetchInstance(moleculeDBID);
			Entity entity = entityMap.get(moleculeDBID);

			// Not sure why the old Perl code stripped
			// out "CHEBI:", but I'll do it here for consistency.
			String chebiID = entity.getChebiId().replaceAll("CHEBI:", ""); 

			String chebiName = entity.getChebiAsciiName();
			List<DataItem> chebiFormulae = entity.getFormulae();

			//String updateRefEntMessage = updateReferenceEntities(molecule, chebiName, instanceEdit);
			//refEntChanges.append(updateRefEntMessage);
			// Now, check to see if we need to update the ReferenceMolecule itself.
			String moleculeIdentifier = (String) molecule.getAttributeValue(ReactomeJavaConstants.identifier);
			String moleculeName = (String) molecule.getAttributeValuesList(ReactomeJavaConstants.name).get(0);
			String moleculeFormulae = (String) molecule.getAttributeValue(ReactomeJavaConstants.formula);

			String prefix = "ReferenceMolecule (DB ID: " + molecule.getDBID() + " / ChEBI ID: " + moleculeIdentifier + ") has changes: ";

			boolean identifierUpdated = updateMoleculeIdentifier(molecule, chebiID, moleculeIdentifier, prefix);
			boolean nameUpdated = updateMoleculeName(molecule, chebiName, moleculeName, prefix);
			boolean formulaUpdated = updateMoleculeFormula(molecule, chebiFormulae, moleculeFormulae, prefix);
			// If the ChEBI name was updated, update the referenceEntities that refer to the molecule.
			if (nameUpdated)
			{
				updateReferenceEntities(molecule, chebiName);
			}
			
			if (identifierUpdated || nameUpdated || formulaUpdated)
			{
				addInstanceEditToExistingModifieds(instanceEdit, molecule);
				// Update the display name.
				InstanceDisplayNameGenerator.setDisplayName(molecule);
				adaptor.updateInstanceAttribute(molecule, ReactomeJavaConstants._displayName);
			}
			if (!testMode)
			{
				if (adaptor.supportsTransactions())
				{
					adaptor.commit();
				}
			}
			else
			{
				adaptor.rollback();
			}
		}
		logger.info("*** Formula-fill changes ***");
		logger.info(this.formulaFillSB.toString());
		logger.info("*** Formula update changes ***");
		logger.info(this.formulaUpdateSB.toString());
		logger.info("*** Name update changes ***");
		logger.info(this.nameSB.toString());
		logger.info("*** Identifier update changes ***");
		logger.info(this.identifierSB.toString());
		logger.info("*** SimpleEntity changes ***");
		logger.info(refEntChanges.toString());
	}

	/**
	 * Update a molecule's formula.
	 * 
	 * @param molecule
	 * @param chebiFormulae
	 * @param moleculeFormulae
	 * @param prefix
	 * @return true if the formula was updated, or if the field was populated (i.e. was NULL before). False, otherwise.
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private boolean updateMoleculeFormula(GKInstance molecule, List<DataItem> chebiFormulae, String moleculeFormulae, String prefix) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		boolean updated = false;
		if (!chebiFormulae.isEmpty())
		{
			String firstFormula = chebiFormulae.get(0).getData();
			if (firstFormula != null)
			{
				if (moleculeFormulae == null)
				{
					this.formulaFillSB.append(prefix).append("New Formula: ").append(firstFormula).append("\n");
					updated = true;
				}
				else if (!firstFormula.equals(moleculeFormulae))
				{
					this.formulaUpdateSB.append(prefix).append(" Old Formula: ").append(moleculeFormulae).append(" ; ").append("New Formula: ").append(firstFormula).append("\n");
					updated = true;
				}
				molecule.setAttributeValue(ReactomeJavaConstants.formula, firstFormula);
				if (!testMode)
				{
					adaptor.updateInstanceAttribute(molecule, ReactomeJavaConstants.formula);
				}

			}
		}
		return updated;
	}

	/**
	 * Updates a molecule's name.
	 * 
	 * @param molecule
	 * @param chebiName
	 * @param moleculeName
	 * @param prefix
	 * @return True if the name was updated. False if not.
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private boolean updateMoleculeName(GKInstance molecule, String chebiName, String moleculeName, String prefix) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		if (!chebiName.equals(moleculeName))
		{
			molecule.setAttributeValue(ReactomeJavaConstants.name, chebiName);
			this.nameSB.append(prefix).append(" Old Name: ").append(moleculeName).append(" ; ").append("New Name: ").append(chebiName).append("\n");
			if (!testMode)
			{
				adaptor.updateInstanceAttribute(molecule, ReactomeJavaConstants.name);
			}
			return true;
		}
		return false;
	}

	/**
	 * Update a molecule's identifier.
	 * 
	 * @param molecule
	 * @param chebiID
	 * @param moleculeIdentifier
	 * @param prefix
	 * @return True if the identifier was updated, false otherwise.
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private boolean updateMoleculeIdentifier(GKInstance molecule, String chebiID, String moleculeIdentifier, String prefix) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		if (!chebiID.equals(moleculeIdentifier))
		{
			molecule.setAttributeValue(ReactomeJavaConstants.identifier, chebiID);
			this.identifierSB.append(prefix).append(" Old Identifier: ").append(moleculeIdentifier).append(" ; ").append("New Identifier: ").append(chebiID).append("\n");
			if (!testMode)
			{
				adaptor.updateInstanceAttribute(molecule, ReactomeJavaConstants.identifier);
			}
			return true;
		}
		return false;
	}

	/**
	 * Updates Objects that refer to a ReferenceMolecule via the "referenceEntity" attribute. The ChEBI name will be appended to the Entities' list of names, at the end. Unless
	 * the ChEBI name is *already* in the list, in which case nothing will happen.
	 * @param molecule The ReferenceMolecule whose referrers need to be updated.
	 * @param chebiName The ChEBI name.
	 * @throws Exception
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 */
	private void updateReferenceEntities(GKInstance molecule, String chebiName) throws Exception, InvalidAttributeException, InvalidAttributeValueException
	{
		// now, update the any Entities that refer to the ReferenceMolecule by appending chebiName to the list of names. TODO: Refactor to a function.
		@SuppressWarnings("unchecked")
		Collection<GKInstance> referrers = molecule.getReferers(ReactomeJavaConstants.referenceEntity);
		if (referrers != null && referrers.size() > 0)
		{
			for (GKInstance referrer : referrers)
			{
				@SuppressWarnings("unchecked")
				List<String> names = (List<String>) referrer.getAttributeValuesList(ReactomeJavaConstants.name);
				if (names == null || names.isEmpty())
				{
					logger.error("Referrer to \"{}\" has a NULL/Empty list of names. This doesn't seem right. Entity in question is: {}", molecule.toString(), referrer.toString());
				}
				else
				{
					// If the first name IS the ChEBI name, then nothing to do. But if not, then need to append.
					if (!names.get(0).equals(chebiName))
					{
						if (!names.contains(chebiName))
						{
							names.add(chebiName);
							referrer.setAttributeValue(ReactomeJavaConstants.name, names);
							adaptor.updateInstanceAttribute(referrer, ReactomeJavaConstants.name);
							logger.info("\"{}\" has been updated; \"{}\" has been added to the list of names: ", referrer.toString(), chebiName, ((List<String>)referrer.getAttributeValuesList(ReactomeJavaConstants.name)).toString());
						}
						else
						{
							logger.info("\"{}\" *already* has \"{}\" as in its list of names: {}", referrer.toString(), chebiName, names.toString());
						}
					}
					else
					{
						logger.info("\"{}\" has \"{}\" as its first name: {}", referrer.toString(), chebiName, names.toString());
					}
				}
			}
		}
	}

	/**
	 * Adds an instanceEdit to an existing list of "modified" objects.
	 * @param instanceEdit The InstanceEdit to add.
	 * @param instance The instance to add the InstanceEdit to.
	 * @throws InvalidAttributeException
	 * @throws Exception
	 * @throws InvalidAttributeValueException
	 */
	private void addInstanceEditToExistingModifieds(GKInstance instanceEdit, GKInstance instance) throws InvalidAttributeException, Exception, InvalidAttributeValueException
	{
		// make sure the "modified" list is loaded.
		instance.getAttributeValuesList(ReactomeJavaConstants.modified);
		// add this instanceEdit to the "modified" list, and update.
		instance.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
		adaptor.updateInstanceAttribute(instance, ReactomeJavaConstants.modified);
	}

	/**
	 * Queries the database for duplicate ChEBI ReferenceMolecules, and prints the
	 * results. A duplicate ChEBI ReferenceMolecule is defined as a
	 * ReferenceMolecule with the same ChEBI Identifier as a different
	 * ReferenceMolecule. No two ReferenceMolecules should share a ChEBI Identifier.
	 * 
	 * @throws SQLException
	 * @throws Exception
	 */
	public void checkForDuplicates() throws SQLException, Exception
	{
		this.duplicatesSB = new StringBuilder();
		String findDuplicateReferenceMolecules = "select ReferenceEntity.identifier, count(ReferenceMolecule.DB_ID)\n"
				+ "from ReferenceMolecule\n"
				+ "inner join ReferenceEntity on ReferenceEntity.DB_ID = ReferenceMolecule.DB_ID\n"
				+ "inner join ReferenceDatabase on ReferenceDatabase.DB_ID = ReferenceEntity.referenceDatabase\n"
				+ "inner join ReferenceDatabase_2_name on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n"
				+ "where ReferenceDatabase_2_name.name = 'ChEBI'\n" + "group by ReferenceEntity.identifier\n"
				+ "having count(ReferenceMolecule.DB_ID) > 1;\n";

		ResultSet duplicates = adaptor.executeQuery(findDuplicateReferenceMolecules, null);
		logger.info("*** Duplicate ReferenceMolecules ***\n");

		Map<Long, GKInstance> duplicateInstanceMap = new HashMap<Long, GKInstance>();

		// Should only be one, but API returns collection.
		@SuppressWarnings("unchecked")
		Collection<GKInstance> chebiDBInsts = (Collection<GKInstance>)adaptor.fetchInstanceByAttribute(ReactomeJavaConstants.ReferenceDatabase, ReactomeJavaConstants.name, "=", "ChEBI");
		GKInstance chebiDBInst = chebiDBInsts.stream().findFirst().orElse(null);
		
		AttributeQueryRequest chebiAQR = adaptor.new AttributeQueryRequest(ReactomeJavaConstants.ReferenceMolecule, ReactomeJavaConstants.referenceDatabase, "=", chebiDBInst);
		
		while (duplicates.next())
		{
			String identifier = duplicates.getString(1);
			int numberOfDuplicates = duplicates.getInt(2);
			this.duplicatesSB.append("** ReferenceMolecule with identifier " + identifier + " occurs " + numberOfDuplicates + " times:\n\n");
			String operator = identifier == null ? "IS NULL" : "=";
			
			AttributeQueryRequest identifierAQR = adaptor.new AttributeQueryRequest(ReactomeJavaConstants.ReferenceMolecule, ReactomeJavaConstants.identifier, operator, identifier);
			
			@SuppressWarnings("unchecked")
			Collection<GKInstance> dupesOfIdentifier = (Collection<GKInstance>) adaptor._fetchInstance(Arrays.asList(chebiAQR, identifierAQR));
			for (GKInstance duplicate : dupesOfIdentifier)
			{
				duplicateInstanceMap.put(duplicate.getDBID(), duplicate);
			}
		}
		for (Long k : duplicateInstanceMap.keySet())
		{
			this.duplicatesSB.append(duplicateInstanceMap.get(k).toStanza()).append("\n");
		}
		duplicates.close();
		if (this.duplicatesSB.length() > 0)
		{
			logger.info(this.duplicatesSB.toString());
		}
		else
		{
			logger.info("No duplicate ChEBI ReferenceMolecules detected.");
		}
	}

	/**
	 * Makes calls to the ChEBI web service to get info for speciefied ChEBI
	 * identifiers.
	 * 
	 * @param updator
	 * @param refMolecules - a list of ReferenceMolecules. The Identifier of each of these molecules will be sent to ChEBI to get up-to-date information for that Identifier.
	 * @param entityMap - a ReferenceMolecule DB_ID-to-ChEBI Entity map. Will be updated by this method.
	 * @param failedEntitiesList - A list of ReferenceMolecules for which no information was returned by ChEBI. Will be updated by this method.
	 */
	private void retrieveUpdatesFromChebi(Collection<GKInstance> refMolecules, Map<Long, Entity> entityMap, List<GKInstance> failedEntitiesList)
	{
		AtomicInteger counter = new AtomicInteger(0);
		// The web service calls are a bit slow to respond, so do them in parallel.
		refMolecules.parallelStream().forEach(molecule ->
		{
			String identifier = null;
			try
			{
				identifier = (String) molecule.getAttributeValue("identifier");
				if (identifier != null && !identifier.trim().equals(""))
				{
					Entity entity = this.chebiClient.getCompleteEntity(identifier);
					if (entity != null)
					{
						entityMap.put(molecule.getDBID(), entity);
					}
					else
					{
						failedEntitiesList.add(molecule);
					}
				}
				else
				{
					logger.error("ERROR: Instance \"{}\" has an empty/null identifier. This should not be allowed.", molecule.toString());
				}
				int i = counter.getAndIncrement();
				if (i % 250 == 0)
				{
					logger.debug("{} ChEBI identifiers checked", i);
				}
			}
			catch (ChebiWebServiceFault_Exception e)
			{
				// "invalid ChEBI identifier" probably shouldn't break execution but should be logged for further investigation.
				if (e.getMessage().contains("invalid ChEBI identifier"))
				{
					logger.error("ERROR: ChEBI Identifier \"{}\" is not formatted correctly.", identifier);
				}
				// Log this identifier, but don't fail.
				else if (e.getMessage().contains("the entity in question is deleted, obsolete, or not yet released"))
				{
					logger.error("ERROR: ChEBI Identifier \"{}\" is deleted, obsolete, or not yet released.", identifier);
				}
				else
				{
					// Other Webservice errors should probably break execution - if one fails, they will all probably fail.
					// This is *not* a general principle, but is based on my experience with the ChEBI webservice specifically -
					// it's a pretty stable service so it's unlikely that if one service call fails, the others will succeed.
					logger.error("WebService error occurred! Message is: {}", e.getMessage());
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}
			catch (InvalidAttributeException e)
			{
				logger.error("InvalidAttributeException caught while trying to get the \"identifier\" attribute on " + molecule.toString());
				// stack trace should be printed, but I don't think this should break execution, though the only way I can think
				// of this happening is if the data model changes - otherwise, this exception would probably never be caught.
				e.printStackTrace();
			}
			catch (Exception e)
			{
				// general exceptions - print stack trace but keep going.
				e.printStackTrace();
			}
		});
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
	public GKInstance createInstanceEdit(MySQLAdaptor adaptor, long personID, String creatorName)
	{
		GKInstance instanceEdit = null;
		try
		{
			instanceEdit = createDefaultIE(adaptor, personID, true, "Inserted by " + creatorName);
			instanceEdit.getDBID();
			adaptor.updateInstance(instanceEdit);
		}
		catch (Exception e)
		{
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
	public static GKInstance createDefaultIE(MySQLAdaptor dba, Long defaultPersonId, boolean needStore, String note) throws Exception
	{
		GKInstance defaultPerson = dba.fetchInstance(defaultPersonId);
		if (defaultPerson != null)
		{
			GKInstance newIE = createDefaultInstanceEdit(defaultPerson);
			newIE.addAttributeValue(ReactomeJavaConstants.dateTime, GKApplicationUtilities.getDateTime());
			newIE.addAttributeValue(ReactomeJavaConstants.note, note);
			InstanceDisplayNameGenerator.setDisplayName(newIE);

			if (needStore)
			{
				dba.storeInstance(newIE);
			}
			else
			{
				logger.info("needStore set to false");
			}
			return newIE;
		}
		else
		{
			throw new Exception("Could not fetch Person entity with ID " + defaultPersonId + ". Please check that a Person entity exists in the database with this ID.");
		}
	}

	/**
	 * Create an InstanceEdit for a specific Person.
	 * @param person The Person.
	 * @return An InstanceEdit.
	 */
	public static GKInstance createDefaultInstanceEdit(GKInstance person)
	{
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
