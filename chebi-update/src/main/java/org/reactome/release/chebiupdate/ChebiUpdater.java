package org.reactome.release.chebiupdate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

			updateReferenceEntities(molecule, chebiName, instanceEdit);

			// Now, check to see if we need to update the ReferenceMolecule itself.
			String moleculeIdentifier = (String) molecule.getAttributeValue(ReactomeJavaConstants.identifier);
			String moleculeName = (String) molecule.getAttributeValuesList(ReactomeJavaConstants.name).get(0);
			String moleculeFormulae = (String) molecule.getAttributeValue(ReactomeJavaConstants.formula);

			String prefix = "ReferenceMolecule (DB ID: " + molecule.getDBID() + " / ChEBI ID: " + moleculeIdentifier + ") has changes: ";

			updateMoleculeIdentifier(molecule, chebiID, moleculeIdentifier, prefix);
			updateMoleculeName(molecule, chebiName, moleculeName, prefix);
			updateMoleculeFormula(molecule, chebiFormulae, moleculeFormulae, prefix);
			// Update the display name.
			InstanceDisplayNameGenerator.setDisplayName(molecule);
			adaptor.updateInstanceAttribute(molecule, ReactomeJavaConstants._displayName);
			
			if (!testMode)
			{
				addInstanceEditToExistingModifieds(instanceEdit, molecule);
				
				if (adaptor.supportsTransactions())
				{
					adaptor.commit();
				}
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
	}

	/**
	 * Update a molecule's formula.
	 * 
	 * @param molecule
	 * @param chebiFormulae
	 * @param moleculeFormulae
	 * @param prefix
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private void updateMoleculeFormula(GKInstance molecule, List<DataItem> chebiFormulae, String moleculeFormulae, String prefix) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		if (!chebiFormulae.isEmpty())
		{
			String firstFormula = chebiFormulae.get(0).getData();
			if (firstFormula != null)
			{
				if (moleculeFormulae == null)
				{
					this.formulaFillSB.append(prefix).append("New Formula: ").append(firstFormula).append("\n");
				}
				else if (!firstFormula.equals(moleculeFormulae))
				{
					this.formulaUpdateSB.append(prefix).append(" Old Formula: ").append(moleculeFormulae).append(" ; ").append("New Formula: ").append(firstFormula).append("\n");
				}
				molecule.setAttributeValue(ReactomeJavaConstants.formula, firstFormula);
				if (!testMode)
				{
					adaptor.updateInstanceAttribute(molecule, ReactomeJavaConstants.formula);
				}

			}
		}
	}

	/**
	 * Updates a molecule's name.
	 * 
	 * @param molecule
	 * @param chebiName
	 * @param moleculeName
	 * @param prefix
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private void updateMoleculeName(GKInstance molecule, String chebiName, String moleculeName, String prefix) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		if (!chebiName.equals(moleculeName))
		{
			molecule.setAttributeValue(ReactomeJavaConstants.name, chebiName);
			this.nameSB.append(prefix).append(" Old Name: ").append(moleculeName).append(" ; ").append("New Name: ").append(chebiName).append("\n");
			if (!testMode)
			{
				adaptor.updateInstanceAttribute(molecule, ReactomeJavaConstants.name);
			}
		}
	}

	/**
	 * Update a molecule's identifier.
	 * 
	 * @param molecule
	 * @param chebiID
	 * @param moleculeIdentifier
	 * @param prefix
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 * @throws Exception
	 */
	private void updateMoleculeIdentifier(GKInstance molecule, String chebiID, String moleculeIdentifier, String prefix) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		if (!chebiID.equals(moleculeIdentifier))
		{
			molecule.setAttributeValue(ReactomeJavaConstants.identifier, chebiID);
			this.identifierSB.append(prefix).append(" Old Identifier: ").append(moleculeIdentifier).append(" ; ").append("New Identifier: ").append(chebiID).append("\n");
			if (!testMode)
			{
				adaptor.updateInstanceAttribute(molecule, ReactomeJavaConstants.identifier);
			}
		}
	}

	/**
	 * Updates ReferenceEntities that refer to ReferenceMolecules. This method will
	 * ensure that the "name" array of a ReferenceEntity associated with
	 * <code>molecule</code> has <code>chebiName</code> as the <em>first</em> name
	 * in its list of names.
	 * 
	 * @param molecule - a ReferenceMolecule. ReferenceEntities that reference this ReferenceMolecule will be updated.
	 * @param chebiName - the name from ChEBI. Should be the first name in the "name" array for any updated ReferenceEntity.
	 * @param instanceEdit - and InstanceEdit that the changes will be associated with.
	 * @throws Exception
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 */
	private void updateReferenceEntities(GKInstance molecule, String chebiName, GKInstance instanceEdit) throws Exception, InvalidAttributeException, InvalidAttributeValueException
	{
		@SuppressWarnings("unchecked")
		Collection<GKInstance> refEntities = (Collection<GKInstance>) molecule.getReferers("referenceEntity");
		if (refEntities != null)
		{
			for (GKInstance refEntity : refEntities)
			{
				@SuppressWarnings("unchecked")
				LinkedList<String> names = new LinkedList<String>(refEntity.getAttributeValuesList("name"));
				// Now we must ensure that the name from the ChEBI molecule is the FIRST name in
				// the referenceEntity's list of names.
				if (!names.isEmpty())
				{
					// name[0] does not match - but before adding chebName, ensure that it's not
					// already somewhere else in the array.
					if (!names.get(0).toLowerCase().equals(chebiName.toLowerCase()))
					{
						int i = 0;
						boolean nameFound = false;
						while (!nameFound && i < names.size())
						{
							String name = names.get(i);
							// We found the chebi name so now we swap.
							if (name.toLowerCase().equals(chebiName.toLowerCase()))
							{
								nameFound = true;
								// Remove the ChEBI name at position i
								names.remove(i);
								// Add the ChEBI name back at position 0.
								names.add(0, chebiName);
								logger.debug("Re-ordering names for {}, names (re-ordered): {}", refEntity.toString(), names);
							}
							i++;
						}
						// If we went through the whole array and didn't find the ChEBI name,
						// we must add it, at the begining of the list of names.
						if (!nameFound)
						{
							names.add(0, chebiName);
							logger.debug("Adding new name to {}, names: {}", refEntity.toString(), names);
						}
					}
				}
				// Update the names of the ReferenceEntity.
				refEntity.setAttributeValue(ReactomeJavaConstants.name, names);
				if (!testMode)
				{
					addInstanceEditToExistingModifieds(instanceEdit, refEntity);
					
					adaptor.updateInstanceAttribute(refEntity, ReactomeJavaConstants.name);
				}
			}
		}
	}

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
		String findDuplicateReferenceMolecules = "select ReferenceEntity.identifier, count(ReferenceMolecule.DB_ID)\n"
				+ "from ReferenceMolecule\n"
				+ "inner join ReferenceEntity on ReferenceEntity.DB_ID = ReferenceMolecule.DB_ID\n"
				+ "inner join ReferenceDatabase on ReferenceDatabase.DB_ID = ReferenceEntity.referenceDatabase\n"
				+ "inner join ReferenceDatabase_2_name on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n"
				+ "where ReferenceDatabase_2_name.name = 'ChEBI'\n" + "group by ReferenceEntity.identifier\n"
				+ "having count(ReferenceMolecule.DB_ID) > 1;\n";

		ResultSet duplicates = adaptor.executeQuery(findDuplicateReferenceMolecules, null);
		logger.info("*** Duplicate ReferenceMolecules ***\n");
		int duplicateIdentifierCount = 0;
		while (duplicates.next())
		{
			duplicateIdentifierCount++;
			String identifier = String.valueOf(duplicates.getInt(1));
			// getInt returns "0" if the value is NULL
			if (identifier.equals("0"))
			{
				identifier = null;
			}
			int numberOfDuplicates = duplicates.getInt(2);
			logger.info("** ReferenceMolecule with identifier {} occurs {} times:\n\n", identifier, numberOfDuplicates);
			Collection<GKInstance> dupesOfIdentifier;
			if (identifier == null)
			{
				AttributeQueryRequest aqrIdentifier = adaptor.new AttributeQueryRequest("ReferenceMolecule", "identifier", "IS NULL", "");
				Collection<GKInstance> refDBs = (Collection<GKInstance>) adaptor.fetchInstanceByAttribute("ReferenceDatabase", "name", "=", "ChEBI");
				AttributeQueryRequest aqrDBName = adaptor.new AttributeQueryRequest("ReferenceMolecule", "referenceDatabase", "=", refDBs.stream().findFirst().get().getDBID());
				
				//dupesOfIdentifier = (Collection<GKInstance>) adaptor.fetchInstanceByAttribute("ReferenceMolecule", "identifier", "IS NULL", "");
				dupesOfIdentifier = (Collection<GKInstance>) adaptor._fetchInstance(Arrays.asList(aqrIdentifier, aqrDBName));
			}
			else
			{
				dupesOfIdentifier = (Collection<GKInstance>) adaptor.fetchInstanceByAttribute("ReferenceMolecule", "identifier", "=", identifier);
			}
			for (GKInstance duplicate : dupesOfIdentifier)
			{
				logger.info(duplicate.toStanza());
			}
		}
		duplicates.close();
		if (duplicateIdentifierCount > 0)
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
		// The web service calls are a bit slow to respond, so do them in parallel.
		refMolecules.parallelStream().forEach(molecule ->
		{
			String identifier = null;
			try
			{
				identifier = (String) molecule.getAttributeValue("identifier");
				if (identifier != null && !identifier.trim().equals(""))
				{
					// Identifiers in Reactome *probably* won't have the "CHEBI:" prefix so we'll have to add it.
					if (!identifier.startsWith("CHEBI:"))
					{
						identifier = "CHEBI:" + identifier;
					}
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
			}
			catch (ChebiWebServiceFault_Exception e)
			{
				try
				{
					logger.error("WebService error: {} ; Identifier that triggered it: {}; Molecule DB ID: {}", e.getMessage(), identifier, molecule.getDBID());
				}
				catch (Exception e1)
				{
					e1.printStackTrace();
				}
				//e.printStackTrace();
				// Webservice error should probably break execution - if one fails, they will all probably fail.
				// This is *not* a general principle, but is based on my experience with the ChEBI webservice specifically -
				// it's a pretty stable service so it's unlikely that if one service call fails, the others will succeed.
				if (!e.getMessage().equals("ERROR (Entity status): the entity in question is deleted, obsolete, or not yet released.")
					&& !e.getMessage().equals("ERROR (CHEBI id): invalid ChEBI identifier - it must be either a number or be a number with the prefix 'CHEBI:'."))
				{
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
