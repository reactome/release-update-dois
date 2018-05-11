package org.reactome.release.chebiupdate;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

import uk.ac.ebi.chebi.webapps.chebiWS.client.ChebiWebServiceClient;
import uk.ac.ebi.chebi.webapps.chebiWS.model.ChebiWebServiceFault_Exception;
import uk.ac.ebi.chebi.webapps.chebiWS.model.DataItem;
import uk.ac.ebi.chebi.webapps.chebiWS.model.Entity;

public class Main
{

	public static void main(String[] args) throws SQLException, Exception
	{
		// Assume the path the the properties file is ./chebi-update.properties
		// but if args[] is not empty, then the first argument must be the path to
		// the resources file.
		String pathToResources = "./chebi-update.properties";
		if (args.length > 0)
		{
			pathToResources = args[0];
		}
		
		Properties props = new Properties();
		props.load(new FileInputStream(pathToResources));
		MySQLAdaptor adaptor = getMySQLAdaptorFromProperties(props);
		boolean testMode = new Boolean(props.getProperty("testMode", "true"));
		
		ChebiUpdater chebiUpdater = new ChebiUpdater(adaptor, testMode);
		
		chebiUpdater.updateChebiReferenceMolecules();
		
//		ChebiWebServiceClient chebiClient = new ChebiWebServiceClient();
//		
//		@SuppressWarnings("unchecked")
//		String chebiRefDBID = (new ArrayList<GKInstance>( adaptor.fetchInstanceByAttribute("ReferenceDatabase", "name", "=", "ChEBI") )).get(0).getDBID().toString();
//		
//		@SuppressWarnings("unchecked")
//		Collection<GKInstance> refMolecules = (Collection<GKInstance>) adaptor.fetchInstanceByAttribute("ReferenceMolecule", "referenceDatabase", "=", chebiRefDBID);
//		
//		// A map: key is the DB_ID of a ReferneceMolecule, value is the uk.ac.ebi.chebi.webapps.chebiWS.model.Entity from ChEBI.
//		Map<Long, Entity> entityMap = Collections.synchronizedMap(new HashMap<Long, Entity>());
//		// A list of the ReferenceMolecules where we could nto get info from ChEBI.
//		List<GKInstance> failedEntiesList = Collections.synchronizedList(new ArrayList<GKInstance>());
//		
//		System.out.println(refMolecules.size() + " ChEBI ReferenceMolecules to check...");
//		
//		// The web service calls are a bit slow to respond, so do them in parallel.
//		refMolecules.parallelStream().forEach(molecule ->
//		{
//			try
//			{
//				String identifier = (String) molecule.getAttributeValue("identifier");
//				Entity entity = chebiClient.getCompleteEntity(identifier);
//				if (entity!=null)
//				{
//					entityMap.put(molecule.getDBID(),entity);
//				}
//				else
//				{
//					failedEntiesList.add(molecule);
//				}
//			}
//			catch (ChebiWebServiceFault_Exception e)
//			{
//				System.err.println("WebService error! ");
//				e.printStackTrace();
//				// Webservice error should probably break execution - if one fails, they will all probably fail.
//				// This is *not* a general principle, but is based on my experience with the ChEBI webservice specifically - 
//				// it's a pretty stable service so it's unlikely that if one service call fails, the others will succeed.
//				throw new RuntimeException(e);
//			}
//			catch (InvalidAttributeException e)
//			{
//				System.err.println("InvalidAttribteException caught while trying to get the \"identifier\" attribute on "+molecule.toString());
//				// stack trace should be printed, but I don't think this should break execution, though the only way I can think
//				// of this happening is if the data model changes - otherwise, this exception should probably never be caught.
//				e.printStackTrace();
//			}
//			catch (Exception e)
//			{
//				// general exceptions - print stack trace but keep going.
//				e.printStackTrace();
//			}
//		} );
//		
//		System.out.println("Number of entities we were able to retrieve information about: "+entityMap.size());
//		System.out.println("Number of entities we were NOT able to retrieve information about: "+failedEntiesList.size());
//		
//		for (GKInstance molecule : failedEntiesList)
//		{
//			System.out.println("Could not get info from ChEBI for: "+molecule.toString());
//		}
//		
//		StringBuilder identifierSB = new StringBuilder();
//		StringBuilder formulaUpdateSB = new StringBuilder();
//		StringBuilder formulaFillSB = new StringBuilder();
//		StringBuilder nameSB = new StringBuilder();
//		
//		for (Long moleculeDBID: entityMap.keySet())
//		{
//			GKInstance molecule = adaptor.fetchInstance(moleculeDBID);
//			Entity entity = entityMap.get(moleculeDBID);
//			
//			String chebiID = entity.getChebiId().replaceAll("CHEBI:", ""); //Not sure why the old Perl code stripped out "CHEBI:", but I'll do it here for consistency.
//			String chebiName = entity.getChebiAsciiName();
//			List<DataItem> chebiFormulae = entity.getFormulae();
//			
//			@SuppressWarnings("unchecked")
//			Collection<GKInstance> refEntities = (Collection<GKInstance>) molecule.getReferers("referenceEntity");
//			
//			for (GKInstance refEntity : refEntities)
//			{
//				@SuppressWarnings("unchecked")
//				LinkedList<String> names = new LinkedList<String>( refEntity.getAttributeValuesList("name") );
//				// Now we must ensure that the name from the ChEBI molecule is the FIRST name in the referenceEntity's list of names.
//				if (!names.isEmpty())
//				{
//					// name[0] does not match - but before adding chebName, ensure that it's not already somewhere else in the array.
//					if (!names.get(0).toLowerCase().equals(chebiName.toLowerCase()))
//					{
//						int i = 0;
//						boolean nameFound = false;
//						while(!nameFound && i < names.size())
//						{
//							String name = names.get(i);
//							// We found the chebi name so now we swap.
//							if (name.toLowerCase().equals(chebiName.toLowerCase()))
//							{
//								nameFound = true;
//								// Remove the ChEBI name at position i
//								names.remove(i);
//								// Add the ChEBI name back at position 0.
//								names.add(0, chebiName);
//							}
//							i++;
//						}
//						// If we went through the whole array and didn't find the ChEBI name,
//						// we must add it, at the begining of the list of names.
//						if (!nameFound)
//						{
//							names.add(0,chebiName);
//						}
//					}
//				}
//				// Update the names of the ReferenceEntity.
//				refEntity.setAttributeValue("name", names);
//				if (!testMode)
//				{
//					adaptor.updateInstanceAttribute(refEntity, "name");
//				}
//			}
//			
//			// Now, check to see if we need to update the ReferenceMolecule itself.
//			String moleculeIdentifier = (String) molecule.getAttributeValue("identifier");
//			String moleculeName = (String) molecule.getAttributeValuesList("name").get(0);
//			String moleculeFormulae = (String) molecule.getAttributeValue("formula");
//			
//			String prefix = "ReferenceMolecule (DB ID: " + molecule.getDBID() + " / ChEBI ID: " + moleculeIdentifier + ") has changes: ";
//			
//			if (!chebiID.equals(moleculeIdentifier))
//			{
//				molecule.setAttributeValue("identifier", chebiID);
//				identifierSB.append(prefix).append(" Old Identifier: ").append(moleculeIdentifier).append(" ; ").append("New Identifier: ").append(chebiID).append("\n");
//				if (!testMode)
//				{
//					adaptor.updateInstanceAttribute(molecule,"identifier");
//				}
//			}
//			if (!chebiName.equals(moleculeName))
//			{
//				molecule.setAttributeValue("name", chebiName);
//				nameSB.append(prefix).append(" Old Name: ").append(moleculeName).append(" ; ").append("New Name: ").append(chebiName).append("\n");
//				if (!testMode)
//				{
//					adaptor.updateInstanceAttribute(molecule,"name");
//				}
//			}
//			if (!chebiFormulae.isEmpty())
//			{
//				String firstFormula = chebiFormulae.get(0).getData();
//				if (firstFormula != null)
//				{
//					if (moleculeFormulae == null)
//					{
//						molecule.setAttributeValue("formula", firstFormula);
//						formulaFillSB.append(prefix).append("New Formula: ").append(firstFormula).append("\n");
//						if (!testMode)
//						{
//							adaptor.updateInstanceAttribute(molecule,"formula");
//						}
//					}
//					else if (!firstFormula.equals(moleculeFormulae))
//					{
//						molecule.setAttributeValue("formula", firstFormula);
//						formulaUpdateSB.append(prefix).append(" Old Formula: ").append(moleculeFormulae).append(" ; ").append("New Formula: ").append(firstFormula).append("\n");
//						if (!testMode)
//						{
//							adaptor.updateInstanceAttribute(molecule,"formula");
//						}
//					}
//				}
//			}
//		}
//		System.out.println("\n*** Formula-fill changes ***\n");
//		System.out.println(formulaFillSB.toString());
//		System.out.println("\n*** Formula update changes ***\n");
//		System.out.println(formulaUpdateSB.toString());
//		System.out.println("\n*** Name update changes ***");
//		System.out.println(nameSB.toString());
//		System.out.println("\n*** Identifier update changes ***\n");
//		System.out.println(identifierSB.toString());
//		
//		// now search for duplicates
//		String findDuplicateReferenceMolecules = "select ReferenceEntity.identifier, count(ReferenceMolecule.DB_ID)\n" + 
//				"from ReferenceMolecule\n" + 
//				"inner join ReferenceEntity on ReferenceEntity.DB_ID = ReferenceMolecule.DB_ID\n" + 
//				"inner join ReferenceDatabase on ReferenceDatabase.DB_ID = ReferenceEntity.referenceDatabase\n" + 
//				"inner join ReferenceDatabase_2_name on ReferenceDatabase_2_name.DB_ID = ReferenceDatabase.DB_ID\n" + 
//				"where ReferenceDatabase_2_name.name = 'ChEBI'\n" + 
//				"group by ReferenceEntity.identifier\n" + 
//				"having count(ReferenceMolecule.DB_ID) > 1;\n";
//		
//		ResultSet duplicates = adaptor.executeQuery(findDuplicateReferenceMolecules, null);
//		System.out.println("\n*** Duplicate ReferenceMolecules ***\n");
//		StringBuilder duplicatesSB = new StringBuilder();
//		while (duplicates.next())
//		{
//			duplicatesSB.append("** ReferenceMolecule with identifier "+duplicates.getInt(1) + " occurs " + duplicates.getInt(2) + " times:\n\n");
//			@SuppressWarnings("unchecked")
//			Collection<GKInstance> dupesOfIdentifier = (Collection<GKInstance>) adaptor.fetchInstanceByAttribute("ReferenceMolecule", "identifier", "=", duplicates.getInt(1));
//			for (GKInstance duplicate : dupesOfIdentifier)
//			{
//				duplicatesSB.append(duplicate.toStanza()).append("\n");
//			}
//		}
//		duplicates.close();
//		if (duplicatesSB.length() > 0)
//		{
//			System.out.println(duplicatesSB.toString());
//		}
//		else
//		{
//			System.out.println("No duplicate ChEBI ReferenceMolecules detected.");
//		}
	}

	private static MySQLAdaptor getMySQLAdaptorFromProperties(Properties props) throws IOException, FileNotFoundException, SQLException
	{
		
		String dbHost = props.getProperty("db.host", "localhost");
		String dbUser = props.getProperty("db.user");
		String dbPassword = props.getProperty("db.password");
		String dbName = props.getProperty("db.name");
		int dbPort = new Integer(props.getProperty("db.port", "3306"));
		MySQLAdaptor adaptor = new MySQLAdaptor(dbHost, dbName, dbUser, dbPassword, dbPort);
		return adaptor;
	}
}
