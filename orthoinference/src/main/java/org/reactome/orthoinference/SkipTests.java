package org.reactome.orthoinference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.pathwaylayout.Utils;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class SkipTests {
	
	private static MySQLAdaptor dba;
//	private static ArrayList<String> skipList = new ArrayList<String>();
	static HashSet<String> skipList = new HashSet<String>();
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}
	
	public static void getSkipList(String skipListFilename) throws NumberFormatException, Exception
	{
		String[] pathwayIdsToSkip = {"162906","168254","977225"};
		for (String pathwayId : pathwayIdsToSkip) 
		{
			GKInstance pathwayInst = dba.fetchInstance(Long.valueOf(pathwayId));
			if (pathwayInst != null)
			{
				List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
				classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Pathway, new String[]{ReactomeJavaConstants.hasEvent}, new String[]{}));
				String[] outClasses = new String[] {ReactomeJavaConstants.ReactionlikeEvent};
				@SuppressWarnings("unchecked")
				Collection<GKInstance> followedInstances = InstanceUtilities.followInstanceAttributes(pathwayInst, classesToFollow, outClasses);
				
				for (GKInstance entity : followedInstances)
				{
					skipList.add(entity.getDBID().toString());
				}
			}
		}
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
	
	public static boolean skipInstance(GKInstance reactionInst) throws NumberFormatException, Exception
	{
		
		boolean inSkipList = skipList.contains(reactionInst.getDBID().toString());
		if (inSkipList)
		{
			return true;
		}
		
		if (reactionInst.getAttributeValue(ReactomeJavaConstants.isChimeric) != null)
		{
			boolean isChimeric = (boolean) reactionInst.getAttributeValue(ReactomeJavaConstants.isChimeric);
			if (isChimeric)
			{
				return true;
			}
		}
		
		if (reactionInst.getAttributeValue("relatedSpecies") != null)
		{
			return true;
		}
		
		if (reactionInst.getAttributeValue(ReactomeJavaConstants.disease) != null)
		{
			return true;
		}
		
		if (reactionInst.getAttributeValue(ReactomeJavaConstants.inferredFrom) != null)
		{
			return true;
		}
		
		Collection<GKInstance> speciesInstances = (Collection<GKInstance>) SkipTests.entitiesContainMultipleSpecies(reactionInst);
		
		if (speciesInstances.size() > 1)
		{
			return true;
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public static Collection<GKInstance> entitiesContainMultipleSpecies(GKInstance reactionInst) throws InvalidAttributeException, Exception
	{
		ArrayList<GKInstance> physicalEntities = new ArrayList<GKInstance>();
		physicalEntities.addAll(reactionInst.getAttributeValuesList(ReactomeJavaConstants.input));
		physicalEntities.addAll(reactionInst.getAttributeValuesList(ReactomeJavaConstants.output));
		for (Object physicalEntityObj : reactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity))
		{
			GKInstance physicalEntity = (GKInstance) physicalEntityObj;
			physicalEntities.addAll(physicalEntity.getAttributeValuesList(ReactomeJavaConstants.physicalEntity));
		}
		ArrayList<GKInstance> regulatedEntities = (ArrayList<GKInstance>) reactionInst.getReferers(ReactomeJavaConstants.regulatedEntity);
		if (regulatedEntities != null) 
		{
			for (GKInstance regulatedEntity : regulatedEntities)
			{
				for (Object regulatorObj : regulatedEntity.getAttributeValuesList(ReactomeJavaConstants.regulator))
				{
					GKInstance regulator = (GKInstance) regulatorObj;
					if (regulator.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity))
					{
						physicalEntities.add(regulator);
					}
				}
			}
		}
		HashMap<String, GKInstance> physicalEntityHash = new HashMap<String, GKInstance>();
		// Remove duplicates using HashMap
		for (GKInstance physicalEntity : physicalEntities)
		{
			physicalEntityHash.put(physicalEntity.getDBID().toString(), physicalEntity);
		}
		HashMap<String, GKInstance> physicalEntitiesFinal = new HashMap<String, GKInstance>();
		for (GKInstance physicalEntity : physicalEntityHash.values())
		{
			physicalEntitiesFinal.put(physicalEntity.getDBID().toString(), physicalEntity);
			Collection<GKInstance> subComponents = SkipTests.recursePhysicalEntityComponents(physicalEntity);
			if (subComponents != null)
			{
				for (GKInstance subComponent : subComponents)
				{
					physicalEntitiesFinal.put(subComponent.getDBID().toString(), subComponent);
				}
			}
		}
		
		HashMap<String, GKInstance> speciesHash = new HashMap<String, GKInstance>();
		for (GKInstance physicalEntity : physicalEntitiesFinal.values())
		{
			if (physicalEntity.getSchemClass().isValidAttribute(ReactomeJavaConstants.species))
			{
				for (Object speciesObj : physicalEntity.getAttributeValuesList(ReactomeJavaConstants.species))
				{
					GKInstance speciesInst = (GKInstance) speciesObj;
					speciesHash.put(speciesInst.getDBID().toString(), speciesInst);
				}
			}
		}
		return speciesHash.values();
	}
	
	public static Collection<GKInstance> recursePhysicalEntityComponents(GKInstance physicalEntity) throws InvalidAttributeException, Exception
	{
		HashMap<String, GKInstance> subComponents = new HashMap<String, GKInstance>();
		if (physicalEntity.getSchemClass().isValidAttribute(ReactomeJavaConstants.hasMember))
		{
			for (Object subComponentObj : physicalEntity.getAttributeValuesList(ReactomeJavaConstants.hasMember))
			{
				GKInstance subComponent = (GKInstance) subComponentObj;
				subComponents.put(subComponent.getDBID().toString(), subComponent);
			}
		}
		if (physicalEntity.getSchemClass().isValidAttribute(ReactomeJavaConstants.hasComponent))
		{
			for (Object subComponentObj : physicalEntity.getAttributeValuesList(ReactomeJavaConstants.hasComponent))
			{
				GKInstance subComponent = (GKInstance) subComponentObj;
				subComponents.put(subComponent.getDBID().toString(), subComponent);
			}
		}
		if (physicalEntity.getSchemClass().isValidAttribute(ReactomeJavaConstants.repeatedUnit))
		{
			for (Object subComponentObj : physicalEntity.getAttributeValuesList(ReactomeJavaConstants.repeatedUnit))
			{
				GKInstance subComponent = (GKInstance) subComponentObj;
				subComponents.put(subComponent.getDBID().toString(), subComponent);
			}
		}
		if (subComponents.size() > 0)
		{
			HashMap<String, GKInstance> subComponentsFinal = new HashMap<String, GKInstance>();
			for (GKInstance subComponent : subComponents.values())
			{	
				subComponentsFinal.put(subComponent.getDBID().toString(), subComponent);
				if (subComponent.getSchemClass().isa(ReactomeJavaConstants.EntitySet) || subComponent.getSchemClass().isa(ReactomeJavaConstants.Complex) || subComponent.getSchemClass().isa(ReactomeJavaConstants.Polymer))
				{
					Collection<GKInstance> subSubComponents = SkipTests.recursePhysicalEntityComponents(subComponent);
					if (subSubComponents != null)
					{
						for (GKInstance subSubComponent : subSubComponents)
						{
							subComponentsFinal.put(subSubComponent.getDBID().toString(), subSubComponent);
						}
					}
				} else {
					continue;
				}
			}
			return subComponentsFinal.values();
		}
		return null;
	}
}
