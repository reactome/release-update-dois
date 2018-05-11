package org.reactome.release.chebiupdate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;

import uk.ac.ebi.chebi.webapps.chebiWS.client.ChebiWebServiceClient;
import uk.ac.ebi.chebi.webapps.chebiWS.model.ChebiWebServiceFault_Exception;
import uk.ac.ebi.chebi.webapps.chebiWS.model.DataItem;
import uk.ac.ebi.chebi.webapps.chebiWS.model.Entity;

public class ChebiUpdater
{
	private boolean testMode = true;
	private MySQLAdaptor adaptor;
	private ChebiWebServiceClient chebiClient = new ChebiWebServiceClient();
	private StringBuilder identifierSB = new StringBuilder();
	private StringBuilder formulaUpdateSB = new StringBuilder();
	private StringBuilder formulaFillSB = new StringBuilder();
	private StringBuilder nameSB = new StringBuilder();
	private StringBuilder duplicatesSB = new StringBuilder();
	
	public ChebiUpdater (MySQLAdaptor adaptor, boolean testMode)
	{
		this.adaptor = adaptor;
		this.testMode = testMode;
	}
	
	/**
	 * Update ChEBI ReferenceMolecules.
	 * This method will query ChEBI for up-to-date information, and using that information it will: <br/>
	 * <ul>
	 * <li>Update the names of ReferenceEntitites that are refer to the ReferenceMolecule</li>
	 * <li>Update the names of ReferenceMolecules</li>
	 * <li>Update the identifiers of ReferenceMolecules</li>
	 * <li>Update the formulae of ReferenceMolecules</li>
	 * </ul>
	 * @throws SQLException
	 * @throws Exception
	 */
	public void updateChebiReferenceMolecules() throws SQLException, Exception
	{
		
		
		@SuppressWarnings("unchecked")
		String chebiRefDBID = (new ArrayList<GKInstance>( adaptor.fetchInstanceByAttribute("ReferenceDatabase", "name", "=", "ChEBI") )).get(0).getDBID().toString();
		
		@SuppressWarnings("unchecked")
		Collection<GKInstance> refMolecules = (Collection<GKInstance>) adaptor.fetchInstanceByAttribute("ReferenceMolecule", "referenceDatabase", "=", chebiRefDBID);
		
		// A map: key is the DB_ID of a ReferneceMolecule, value is the uk.ac.ebi.chebi.webapps.chebiWS.model.Entity from ChEBI.
		Map<Long, Entity> entityMap = Collections.synchronizedMap(new HashMap<Long, Entity>());
		// A list of the ReferenceMolecules where we could nto get info from ChEBI.
		List<GKInstance> failedEntiesList = Collections.synchronizedList(new ArrayList<GKInstance>());
		
		System.out.println(refMolecules.size() + " ChEBI ReferenceMolecules to check...");
		
		retrieveUpdatesFromChebi(refMolecules, entityMap, failedEntiesList);
		
		System.out.println("Number of entities we were able to retrieve information about: "+entityMap.size());
		System.out.println("Number of entities we were NOT able to retrieve information about: "+failedEntiesList.size());

		for (GKInstance molecule : failedEntiesList)
		{
			System.out.println("Could not get info from ChEBI for: "+molecule.toString());
		}
		

		
		for (Long moleculeDBID: entityMap.keySet())
		{
			GKInstance molecule = adaptor.fetchInstance(moleculeDBID);
			Entity entity = entityMap.get(moleculeDBID);
			
			String chebiID = entity.getChebiId().replaceAll("CHEBI:", ""); //Not sure why the old Perl code stripped out "CHEBI:", but I'll do it here for consistency.
			String chebiName = entity.getChebiAsciiName();
			List<DataItem> chebiFormulae = entity.getFormulae();
			
			updateReferenceEntities(molecule, chebiName);
			
			// Now, check to see if we need to update the ReferenceMolecule itself.
			String moleculeIdentifier = (String) molecule.getAttributeValue("identifier");
			String moleculeName = (String) molecule.getAttributeValuesList("name").get(0);
			String moleculeFormulae = (String) molecule.getAttributeValue("formula");
			
			String prefix = "ReferenceMolecule (DB ID: " + molecule.getDBID() + " / ChEBI ID: " + moleculeIdentifier + ") has changes: ";
			
			updateMoleculeIdentifier( molecule, chebiID, moleculeIdentifier, prefix);
			updateMoleculeName(molecule, chebiName, moleculeName, prefix);
			updateMoleculeFormula(molecule, chebiFormulae, moleculeFormulae, prefix);
		}
		System.out.println("\n*** Formula-fill changes ***\n");
		System.out.println(this.formulaFillSB.toString());
		System.out.println("\n*** Formula update changes ***\n");
		System.out.println(this.formulaUpdateSB.toString());
		System.out.println("\n*** Name update changes ***");
		System.out.println(this.nameSB.toString());
		System.out.println("\n*** Identifier update changes ***\n");
		System.out.println(this.identifierSB.toString());
	}

	/**
	 * Update a molecule's formula.
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
					molecule.setAttributeValue("formula", firstFormula);
					this.formulaFillSB.append(prefix).append("New Formula: ").append(firstFormula).append("\n");
					if (!testMode)
					{
						adaptor.updateInstanceAttribute(molecule,"formula");
					}
				}
				else if (!firstFormula.equals(moleculeFormulae))
				{
					molecule.setAttributeValue("formula", firstFormula);
					this.formulaUpdateSB.append(prefix).append(" Old Formula: ").append(moleculeFormulae).append(" ; ").append("New Formula: ").append(firstFormula).append("\n");
					if (!testMode)
					{
						adaptor.updateInstanceAttribute(molecule,"formula");
					}
				}
			}
		}
	}

	/**
	 * Updates a molecule's name.
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
			molecule.setAttributeValue("name", chebiName);
			this.nameSB.append(prefix).append(" Old Name: ").append(moleculeName).append(" ; ").append("New Name: ").append(chebiName).append("\n");
			if (!testMode)
			{
				adaptor.updateInstanceAttribute(molecule,"name");
			}
		}
	}

	/**
	 * Update a molecule's identifier.
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
			molecule.setAttributeValue("identifier", chebiID);
			this.identifierSB.append(prefix).append(" Old Identifier: ").append(moleculeIdentifier).append(" ; ").append("New Identifier: ").append(chebiID).append("\n");
			if (!testMode)
			{
				adaptor.updateInstanceAttribute(molecule,"identifier");
			}
		}
	}

	/**
	 * Updates ReferenceEntities that refer to ReferenceMolecules. 
	 * This method will ensure that the "name" array of a ReferenceEntity associated with <code>molecule</code> has <code>chebiName</code> as the <em>first</em> name in
	 * its list of names.
	 * @param molecule - a ReferenceMolecule. ReferenceEntities that reference this ReferenceMolecule will be updated.
	 * @param chebiName - the name from ChEBI. Should be the first name in the "name" array for any updated ReferenceEntity.
	 * @throws Exception
	 * @throws InvalidAttributeException
	 * @throws InvalidAttributeValueException
	 */
	private void updateReferenceEntities(GKInstance molecule, String chebiName) throws Exception, InvalidAttributeException, InvalidAttributeValueException
	{
		@SuppressWarnings("unchecked")
		Collection<GKInstance> refEntities = (Collection<GKInstance>) molecule.getReferers("referenceEntity");
		
		for (GKInstance refEntity : refEntities)
		{
			@SuppressWarnings("unchecked")
			LinkedList<String> names = new LinkedList<String>( refEntity.getAttributeValuesList("name") );
			// Now we must ensure that the name from the ChEBI molecule is the FIRST name in the referenceEntity's list of names.
			if (!names.isEmpty())
			{
				// name[0] does not match - but before adding chebName, ensure that it's not already somewhere else in the array.
				if (!names.get(0).toLowerCase().equals(chebiName.toLowerCase()))
				{
					int i = 0;
					boolean nameFound = false;
					while(!nameFound && i < names.size())
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
						}
						i++;
					}
					// If we went through the whole array and didn't find the ChEBI name,
					// we must add it, at the begining of the list of names.
					if (!nameFound)
					{
						names.add(0,chebiName);
					}
				}
			}
			// Update the names of the ReferenceEntity.
			refEntity.setAttributeValue("name", names);
			if (!testMode)
			{
				adaptor.updateInstanceAttribute(refEntity, "name");
			}
		}
	}

	/**
	 * Queries the database for duplicate ChEBI ReferenceMolecules, and prints the results.
	 * A duplicate ChEBI ReferenceMolecule is defined as a ReferenceMolecule with the same
	 * ChEBI Identifier as a different ReferenceMolecule. No two ReferenceMolecules
	 * should share a ChEBI Identifier. 
	 * @throws SQLException
	 * @throws Exception
	 */
	public void checkForDuplicates() throws SQLException, Exception
	{
		String findDuplicateReferenceMolecules = "select ReferenceEntity.identifier, count(ReferenceMolecule.DB_ID)\n" + 
				"from ReferenceMolecule\n" + 
				"inner join ReferenceEntity on ReferenceEntity.DB_ID = ReferenceMolecule.DB_ID\n" + 
				"inner join ReferenceDatabase on ReferenceDatabase.DB_ID = ReferenceEntity.referenceDatabase\n" + 
				"inner join ReferenceDatabase_2_name on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" + 
				"where ReferenceDatabase_2_name.name = 'ChEBI'\n" + 
				"group by ReferenceEntity.identifier\n" + 
				"having count(ReferenceMolecule.DB_ID) > 1;\n";
		
		ResultSet duplicates = adaptor.executeQuery(findDuplicateReferenceMolecules, null);
		System.out.println("\n*** Duplicate ReferenceMolecules ***\n");
		
		while (duplicates.next())
		{
			int identifier = duplicates.getInt(1);
			int numberOfDuplicates = duplicates.getInt(2);
			this.duplicatesSB.append("** ReferenceMolecule with identifier "+ identifier + " occurs " + numberOfDuplicates + " times:\n\n");
			@SuppressWarnings("unchecked")
			Collection<GKInstance> dupesOfIdentifier = (Collection<GKInstance>) adaptor.fetchInstanceByAttribute("ReferenceMolecule", "identifier", "=", identifier);
			for (GKInstance duplicate : dupesOfIdentifier)
			{
				this.duplicatesSB.append(duplicate.toStanza()).append("\n");
			}
		}
		duplicates.close();
		if (this.duplicatesSB.length() > 0)
		{
			System.out.println(this.duplicatesSB.toString());
		}
		else
		{
			System.out.println("No duplicate ChEBI ReferenceMolecules detected.");
		}
	}

	/**
	 * Makes calls to the ChEBI web service to get info for speciefied ChEBI identifiers.
	 * @param updator
	 * @param refMolecules - a list of ReferenceMolecules. The Identifier of each of these molecules will be sent to ChEBI to get up-to-date information for that Identifier.
	 * @param entityMap - a ReferenceMolecule DB_ID-to-ChEBI Entity map. Will be updated by this method.
	 * @param failedEntiesList - A lust of ReferenceMolecules for which no information was returned by ChEBI. Will be updated by this method.
	 */
	private void retrieveUpdatesFromChebi(Collection<GKInstance> refMolecules, Map<Long, Entity> entityMap, List<GKInstance> failedEntiesList)
	{
		// The web service calls are a bit slow to respond, so do them in parallel.
		refMolecules.parallelStream().forEach(molecule ->
		{
			try
			{
				String identifier = (String) molecule.getAttributeValue("identifier");
				Entity entity = this.chebiClient.getCompleteEntity(identifier);
				if (entity!=null)
				{
					entityMap.put(molecule.getDBID(),entity);
				}
				else
				{
					failedEntiesList.add(molecule);
				}
			}
			catch (ChebiWebServiceFault_Exception e)
			{
				System.err.println("WebService error! ");
				e.printStackTrace();
				// Webservice error should probably break execution - if one fails, they will all probably fail.
				// This is *not* a general principle, but is based on my experience with the ChEBI webservice specifically - 
				// it's a pretty stable service so it's unlikely that if one service call fails, the others will succeed.
				throw new RuntimeException(e);
			}
			catch (InvalidAttributeException e)
			{
				System.err.println("InvalidAttribteException caught while trying to get the \"identifier\" attribute on "+molecule.toString());
				// stack trace should be printed, but I don't think this should break execution, though the only way I can think
				// of this happening is if the data model changes - otherwise, this exception should probably never be caught.
				e.printStackTrace();
			}
			catch (Exception e)
			{
				// general exceptions - print stack trace but keep going.
				e.printStackTrace();
			}
		} );
	}
	
}
