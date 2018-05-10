package org.reactome.release.chebiupdate;

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

import uk.ac.ebi.chebi.webapps.chebiWS.client.ChebiWebServiceClient;
import uk.ac.ebi.chebi.webapps.chebiWS.model.ChebiWebServiceFault_Exception;
import uk.ac.ebi.chebi.webapps.chebiWS.model.DataItem;
import uk.ac.ebi.chebi.webapps.chebiWS.model.Entity;

public class Main {

	public static void main(String[] args) throws SQLException, Exception
	{

		ChebiWebServiceClient chebiClient = new ChebiWebServiceClient();
		MySQLAdaptor adaptor = new MySQLAdaptor("localhost", "", "", "");
		Collection<GKInstance> refMolecules = adaptor.fetchInstancesByClass("ReferenceMolecule");
		
		Map<GKInstance,Entity> entityMap = Collections.synchronizedMap(new HashMap<GKInstance,Entity>());
		List<GKInstance> failedEntiesList = Collections.synchronizedList(new ArrayList<GKInstance>());
		refMolecules.parallelStream().forEach(molecule ->
		{
			try
			{
				//TODO: Include the "referenceDatabase=ChEBI" requirement in the initial query
				if ( ((GKInstance)molecule.getAttributeValue("referenceDatabase")).getDisplayName().contains("ChEBI"))
				{
					try
					{
						String identifier = (String) molecule.getAttributeValue("identifier");
						Entity entity = chebiClient.getCompleteEntity(identifier);
						if (entity!=null)
						{
							entityMap.put(molecule,entity);
						}
						else
						{
							failedEntiesList.add(molecule);
						}
						//System.out.println(entity.getChebiAsciiName());
					}
					catch (ChebiWebServiceFault_Exception e)
					{
						e.printStackTrace();
					}
					catch (InvalidAttributeException e)
					{
						e.printStackTrace();
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
					
				}
				else
				{
					System.out.println("Molecule "+molecule.toString()+" is not from ChEBI, it is from " + ((GKInstance)molecule.getAttributeValue("referenceDatabase")).getDisplayName());
				}
			}
			catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} );
		
		System.out.println("Number of entities we were able to retrieve information about: "+entityMap.size());
		System.out.println("Number of entities we were NOT able to retrieve information about: "+failedEntiesList.size());
		
		for (GKInstance molecule: entityMap.keySet())
		{
			Entity entity = entityMap.get(molecule);
			
			String chebiID = entity.getChebiId().replaceAll("CHEBI:", ""); //Not sure why the old Perl code stripped out "CHEBI:", but I'll do it here for consistency.
			String chebiName = entity.getChebiAsciiName();
			List<DataItem> chebiFormulae = entity.getFormulae();
			
			Collection<GKInstance> refEntities = molecule.getReferers("referenceEntity");
			
			for (GKInstance refEntity : refEntities)
			{
				LinkedList<String> names = new LinkedList<String>( refEntity.getAttributeValuesList("name") );
//				System.out.println("reference entity names:" + names);
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
								String nameToMove = names.remove(i);
								names.add(0, chebiName);
							}
							i++;
						}
						// If we went through the whole array and didn't find the ChEBI name, we must add it.
						if (!nameFound)
						{
							names.add(0,chebiName);
						}
					}
				}
				// Update the names of the ReferenceEntity.
//				refEntity.setAttributeValue("name", names);
//				adaptor.updateInstanceAttribute(refEntity, "name");;
				
				//System.out.println("ChEBI Name: " + chebiName + " ; names list: " + names.toString());
			}
			
			// Now, check to see if we need to update the ReferenceMolecule itself.
			String moleculeIdentifier = (String) molecule.getAttributeValue("identifier");
			String moleculeName = (String) molecule.getAttributeValuesList("name").get(0);
			String moleculeFormulae = (String) molecule.getAttributeValue("formula");
			StringBuilder sb = new StringBuilder();
			
			// We have an Identifier OR Name OR Formulae mismatch with ChEBI:
			if (!chebiID.equals(moleculeIdentifier))
			{
				molecule.setAttributeValue("identifier", chebiID);
				sb.append(" Old Identifier: ").append(moleculeIdentifier).append(" ; ").append("New Identifier: ").append(chebiID);
			}
			if (!chebiName.equals(moleculeName))
			{
				molecule.setAttributeValue("name", chebiName);
				sb.append(" Old Name: ").append(moleculeName).append(" ; ").append("New Name: ").append(chebiName);
			}
			if (!chebiFormulae.isEmpty() && !chebiFormulae.get(0).getData().equals(moleculeFormulae))
			{
				molecule.setAttributeValue("formula", chebiFormulae.get(0).getData());
				sb.append(" Old Formula: ").append(moleculeFormulae).append(" ; ").append("New Formula: ").append(chebiFormulae.get(0).getData());
			}
			if (sb.length() > 0)
			{
				// TODO: Output this to a WikiMedia-friendly format.
				System.out.println( "ReferenceMolecule (DB ID: " + molecule.getDBID() + " / ChEBI ID: " + moleculeIdentifier + ") has changes: "+ sb.toString());
			}
			//TODO: actually commit the database changes.
		}
	}

}
