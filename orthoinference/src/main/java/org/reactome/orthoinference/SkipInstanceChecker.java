package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class SkipTests {
	
	private static MySQLAdaptor dba;
	static Set<String> skipList = new HashSet<String>();
	
	// Skiplist was traditionally provided in a file, but since it's currently just 3 instances, I've just hard-coded them here.
	public static void getSkipList(String skipListFilename) throws NumberFormatException, Exception
	{
		String[] pathwayIdsToSkip = {"162906","168254","977225"};
		for (String pathwayId : pathwayIdsToSkip) 
		{
			GKInstance pathwayInst = dba.fetchInstance(Long.valueOf(pathwayId));
			if (pathwayInst != null)
			{
				// Finds all ReactionLikeEvents associated with the skiplists Pathway and hasEvent attributes, and adds them to skiplist.
				List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
				classesToFollow.add(new ClassAttributeFollowingInstruction(Pathway, new String[]{hasEvent}, new String[]{}));
				String[] outClasses = new String[] {ReactionlikeEvent};
				@SuppressWarnings("unchecked")
				Collection<GKInstance> followedInstances = InstanceUtilities.followInstanceAttributes(pathwayInst, classesToFollow, outClasses);
				
				for (GKInstance entityInst : followedInstances)
				{
					skipList.add(entityInst.getDBID().toString());
				}
			}
		}
		// Generates skiplist
		String skipListFilePath = "src/main/resources/" + skipListFilename; 
		FileReader fr = new FileReader(skipListFilePath);
		BufferedReader br = new BufferedReader(fr);
		String currentLine;
		while ((currentLine = br.readLine()) != null)
		{
			skipList.add(currentLine.trim());
		}
		br.close();
		fr.close();
	}
	// Skip orthoinference of this instance if:
	public static boolean checkIfInstanceShouldBeSkipped(GKInstance reactionInst) throws NumberFormatException, Exception
	{
		// it is found in skiplist array
		if (skipList.contains(reactionInst.getDBID().toString()))
		{
			return true;
		}
		// it is chimeric
		if (reactionInst.getAttributeValue(isChimeric) != null)
		{
			if ((boolean) reactionInst.getAttributeValue(isChimeric))
			{
				return true;
			}
		}
		// it has related species
		if (reactionInst.getAttributeValue("relatedSpecies") != null)
		{
			return true;
		}
		// it is a disease reaction
		if (reactionInst.getAttributeValue(disease) != null)
		{
			return true;
		}
		// it is manually inferred
		if (reactionInst.getAttributeValue(inferredFrom) != null)
		{
			return true;
		}
		// it contains multiple species
		Collection<GKInstance> speciesInstances = (Collection<GKInstance>) entitiesContainMultipleSpecies(reactionInst);
		if (speciesInstances.size() > 1)
		{
			return true;
		}
		return false;
	}
	
	// Goes through all input/output/catalystActivity/regulatedBy attribute instances, and captures all species associates with them. Returns a collection of species instances.
	@SuppressWarnings("unchecked")
	private static Collection<GKInstance> entitiesContainMultipleSpecies(GKInstance reactionInst) throws InvalidAttributeException, Exception
	{
		List<GKInstance> physicalEntityInstances = new ArrayList<GKInstance>();
		physicalEntityInstances.addAll(reactionInst.getAttributeValuesList(input));
		physicalEntityInstances.addAll(reactionInst.getAttributeValuesList(output));
		for (GKInstance catalystActivityInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(catalystActivity))
		{
			physicalEntityInstances.addAll(catalystActivityInst.getAttributeValuesList(physicalEntity));
		}
		List<GKInstance> regulatedByInstances = (ArrayList<GKInstance>) reactionInst.getAttributeValuesList("regulatedBy");

		if (regulatedByInstances != null) 
		{
			for (GKInstance regulatedByInst : regulatedByInstances)
			{
				for (GKInstance regulatorInst : (Collection<GKInstance>) regulatedByInst.getAttributeValuesList(regulator))
				{
					if (regulatorInst.getSchemClass().isa(PhysicalEntity))
					{
						physicalEntityInstances.add(regulatorInst);
					}
				}
			}
		}
		Map<String, GKInstance> physicalEntityHash = new HashMap<String, GKInstance>();
		// Remove duplicates using HashMap
		for (GKInstance physicalEntityInst : physicalEntityInstances)
		{
			physicalEntityHash.put(physicalEntityInst.getDBID().toString(), physicalEntityInst);
		}
		Map<String, GKInstance> physicalEntitiesFinal = new HashMap<String, GKInstance>();
		for (GKInstance physicalEntityInst : physicalEntityHash.values())
		{
			physicalEntitiesFinal.put(physicalEntityInst.getDBID().toString(), physicalEntityInst);
			Collection<GKInstance> allConstituentInstances = recursePhysicalEntityConstituentInstances(physicalEntityInst);
			if (allConstituentInstances != null)
			{
				for (GKInstance constituentInst : allConstituentInstances)
				{
					physicalEntitiesFinal.put(constituentInst.getDBID().toString(), constituentInst);
				}
			}
		}
		Map<String, GKInstance> speciesHash = new HashMap<String, GKInstance>();
		for (GKInstance physicalEntityInst : physicalEntitiesFinal.values())
		{
			if (physicalEntityInst.getSchemClass().isValidAttribute(species))
			{
				for (GKInstance speciesInst : (Collection<GKInstance>) physicalEntityInst.getAttributeValuesList(species))
				{
					speciesHash.put(speciesInst.getDBID().toString(), speciesInst);
				}
			}
		}
		return speciesHash.values();
	}
	
	// Looks at referrals of the constituent instances for the species attribute as well
	// The term 'constituent' is used as a catch-all for instances under the hasMember/hasComponent/repeatedUnit attributes
	@SuppressWarnings("unchecked")
	private static Collection<GKInstance> recursePhysicalEntityConstituentInstances(GKInstance physicalEntity) throws InvalidAttributeException, Exception
	{
		Map<String, GKInstance> constituentInstances = new HashMap<String, GKInstance>();
		if (physicalEntity.getSchemClass().isValidAttribute(hasMember))
		{
			for (GKInstance memberInst : (Collection<GKInstance>) physicalEntity.getAttributeValuesList(hasMember))
			{
				constituentInstances.put(memberInst.getDBID().toString(), memberInst);
			}
		}
		if (physicalEntity.getSchemClass().isValidAttribute(hasComponent))
		{
			for (GKInstance componentInst : (Collection<GKInstance>) physicalEntity.getAttributeValuesList(hasComponent))
			{
				constituentInstances.put(componentInst.getDBID().toString(), componentInst);
			}
		}
		if (physicalEntity.getSchemClass().isValidAttribute(repeatedUnit))
		{
			for (GKInstance repeatedUnitInst : (Collection<GKInstance>) physicalEntity.getAttributeValuesList(repeatedUnit))
			{
				constituentInstances.put(repeatedUnitInst.getDBID().toString(), repeatedUnitInst);
			}
		}
		if (constituentInstances.size() > 0)
		{
			Map<String, GKInstance> finalConstituentInstancesMap = new HashMap<String, GKInstance>();
			for (GKInstance constituentInst : constituentInstances.values())
			{	
				finalConstituentInstancesMap.put(constituentInst.getDBID().toString(), constituentInst);
				if (constituentInst.getSchemClass().isa(EntitySet) || constituentInst.getSchemClass().isa(Complex) || constituentInst.getSchemClass().isa(Polymer))
				{
					Collection<GKInstance> recursedConstituentInstances = recursePhysicalEntityConstituentInstances(constituentInst);
					if (recursedConstituentInstances != null)
					{
						for (GKInstance recursedConstituentInst : recursedConstituentInstances)
						{
							finalConstituentInstancesMap.put(recursedConstituentInst.getDBID().toString(), recursedConstituentInst);
						}
					}
				} else {
					continue;
				}
			}
			return finalConstituentInstancesMap.values();
		}
		return null;
	}
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}
}
