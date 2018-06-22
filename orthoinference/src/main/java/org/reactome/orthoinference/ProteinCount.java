package org.reactome.orthoinference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;

public class ProteinCount {
	
	private static HashMap<String, String[]> homologueMappings = new HashMap<String,String[]>();
	
	public static void setHomologueMappingFile(HashMap<String, String[]> homologueMappingsCopy) throws IOException
	{
		homologueMappings = homologueMappingsCopy;
	}
	// TODO: Function descriptions; max > total check;
	public static List<Integer> countDistinctProteins (GKInstance instanceToBeInferred) throws InvalidAttributeException, Exception
	{
		List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.ReactionlikeEvent, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.CatalystActivity, new String[]{ReactomeJavaConstants.physicalEntity}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Complex, new String[]{ReactomeJavaConstants.hasComponent}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Polymer, new String[]{ReactomeJavaConstants.repeatedUnit}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.EntityWithAccessionedSequence, new String[]{ReactomeJavaConstants.referenceEntity}, new String[]{}));
		
		String[] outClasses = new String[] {ReactomeJavaConstants.ReferenceGeneProduct, ReactomeJavaConstants.EntitySet};
		@SuppressWarnings("unchecked")
		Collection<GKInstance> followedInstances = InstanceUtilities.followInstanceAttributes(instanceToBeInferred, classesToFollow, outClasses);		

		List<Integer> distinctProteinCounts = new ArrayList<Integer>();
		int total = 0;
		int inferred = 0;
		int max = 0;
		
		for (GKInstance entity : followedInstances)
		{
			if (entity.getSchemClass().isa(ReactomeJavaConstants.ReferenceGeneProduct))
			{
				int count = 0;
				String identifier = entity.getAttributeValue(ReactomeJavaConstants.identifier).toString();
				if (homologueMappings.get(identifier) != null)
				{
					count = homologueMappings.get(identifier).length;
				}
				total++;
				if (count > max)
				{
					max = count;
				}
				if (count > 0)
				{
					inferred++;
				}
			}
		}
	
		for (GKInstance entity : followedInstances)
		{
			if (entity.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
			{
				List<ClassAttributeFollowingInstruction> entitySetsInstancesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
				entitySetsInstancesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.DefinedSet, new String[]{ReactomeJavaConstants.hasMember}, new String[]{}));
				entitySetsInstancesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.CandidateSet, new String[]{ReactomeJavaConstants.hasMember}, new String[]{}));
				entitySetsInstancesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.EntityWithAccessionedSequence, new String[]{ReactomeJavaConstants.referenceEntity}, new String[]{}));
				
				String[] entitySetsOutClasses = new String[] {ReactomeJavaConstants.Complex, ReactomeJavaConstants.Polymer, ReactomeJavaConstants.ReferenceSequence};
				@SuppressWarnings("unchecked")
				Collection<GKInstance> entitySetsFollowedInstances = InstanceUtilities.followInstanceAttributes(entity, entitySetsInstancesToFollow, entitySetsOutClasses);
				if (entitySetsFollowedInstances.size() == 0 && entity.getSchemClass().isa(ReactomeJavaConstants.CandidateSet))
				{
					List<Integer> checkedCandidates = ProteinCount.checkCandidates(entity, followedInstances);
					if (checkedCandidates.size() > 0) {
						total += checkedCandidates.get(0);
						if (checkedCandidates.size() > 1) 
						{
							inferred += checkedCandidates.get(1);
						}
						if (checkedCandidates.size() > 2) 
						{
							max += checkedCandidates.get(2);
						}
					}
					continue;
				}
				//TODO: next unless first element of array
				if (entitySetsFollowedInstances.size() > 0)
				{
					boolean uncountedInstances = false;
					for (GKInstance physicalEntity : entitySetsFollowedInstances)
					{
						for (GKInstance earlyFollowedInstance : followedInstances)
						{
							if (physicalEntity.getAttributeValue(ReactomeJavaConstants.DB_ID) == earlyFollowedInstance.getAttributeValue(ReactomeJavaConstants.DB_ID))
							{
								continue;
							}
						}
						uncountedInstances = true;
					}
					if (!uncountedInstances)
					{
						continue;
					}
					int flag = 0;
					int flagInferred = 0;
					for (GKInstance physicalEntity : entitySetsFollowedInstances)
					{
						if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex) || physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Polymer))
						{
							List<Integer> countedComplexProteins = ProteinCount.countDistinctProteins(physicalEntity);
							if (countedComplexProteins != null)
							{
								int subTotal = countedComplexProteins.get(0);
								int subInferred = countedComplexProteins.get(1);
								int subMax = countedComplexProteins.get(2);
								
								if (subTotal > flag)
								{
									flag = subTotal;
								}
								if (subInferred > flagInferred)
								{
									flagInferred = subInferred;
								}
								if (subMax > max)
								{
									max = subMax;
								}
							}
						} else if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.ReferenceGeneProduct))
						{
							flag = 1;
							String identifier = physicalEntity.getAttributeValue(ReactomeJavaConstants.identifier).toString();
							int count = 0;
							if (homologueMappings.get(identifier) != null)
							{
								count = homologueMappings.get(identifier).length;
							}
							if (count > max)
							{
								max = count;
							}
							if (count > 0)
							{
								flagInferred = 1;
							}
						} 
					}
					total += flag;
					inferred += flagInferred;
				}
			}
		}
		distinctProteinCounts.add(total);
		distinctProteinCounts.add(inferred);
		distinctProteinCounts.add(max);
		return distinctProteinCounts;
	}
	
	public static List<Integer> checkCandidates(GKInstance candidateSet, Collection<GKInstance> followedInstances) throws InvalidAttributeException, Exception
	{
		List<Integer> checkedCandidates = new ArrayList<Integer>();
		if (candidateSet.getAttributeValue(ReactomeJavaConstants.hasCandidate) != null)
		{
			int candidateTotal = 0;
			int candidateInferred = 0;
			int candidateMax = 0;
			boolean flag = false;
			List<ClassAttributeFollowingInstruction> candidateSetInstancesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
			candidateSetInstancesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.CandidateSet, new String[]{ReactomeJavaConstants.hasCandidate}, new String[]{}));
			candidateSetInstancesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.EntityWithAccessionedSequence, new String[]{ReactomeJavaConstants.referenceEntity}, new String[]{}));
			String[] candidateSetOutClasses = new String[] {ReactomeJavaConstants.Complex, ReactomeJavaConstants.Polymer, ReactomeJavaConstants.ReferenceSequence};
			@SuppressWarnings("unchecked")
			Collection<GKInstance> candidateSetFollowedInstances = InstanceUtilities.followInstanceAttributes(candidateSet, candidateSetInstancesToFollow, candidateSetOutClasses);
			
			boolean uncountedInstances = false;
			for (GKInstance physicalEntity : candidateSetFollowedInstances)
			{
				for (GKInstance earlyFollowedInstance : followedInstances)
				{
					if (physicalEntity.getAttributeValue(ReactomeJavaConstants.DB_ID) == earlyFollowedInstance.getAttributeValue(ReactomeJavaConstants.DB_ID))
					{
						continue;
					}
				}
				uncountedInstances = true;
			}
			if (!uncountedInstances)
			{
				return checkedCandidates;
			}
			for (GKInstance physicalEntity : candidateSetFollowedInstances)
			{
				if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex) || physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Polymer))
				{
					List<Integer> candidateComplexCounts = ProteinCount.countDistinctProteins(physicalEntity);
					if (candidateComplexCounts.size() > 0)
					{
						if (candidateComplexCounts.get(0) > 0 && candidateComplexCounts.get(1) == 0)
						{
							flag = true;
						}
						if (candidateTotal > 0 && candidateComplexCounts.get(0) > candidateTotal)
						{
							candidateTotal = candidateComplexCounts.get(0);
						}
						if (candidateInferred > 0 && candidateComplexCounts.get(1) > candidateInferred)
						{
							candidateInferred = candidateComplexCounts.get(1);
						}
						if (candidateMax > 0 && candidateComplexCounts.get(2) > candidateMax)
						{
							candidateMax = candidateComplexCounts.get(2);
						}
					}
				} else if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.ReferenceGeneProduct))
				{
					candidateTotal = 1;
					String identifier = physicalEntity.getAttributeValue(ReactomeJavaConstants.identifier).toString();
					int count = 0;
					if (homologueMappings.get(identifier) != null)
					{
						count = homologueMappings.get(identifier).length;
					}
					// TODO: Is it possible to set c to a value?
					if (count > 0)
					{
						candidateInferred = 1;
					}
					if (candidateInferred == 0)
					{
						flag = true;
					}
				}
			}
			if (flag)
			{
				checkedCandidates.add(candidateTotal);
				checkedCandidates.add(0); // candidateInferred value is dropped, 0 is returned instead
				return checkedCandidates;
			}
			checkedCandidates.add(candidateTotal);
			checkedCandidates.add(candidateInferred);
			checkedCandidates.add(candidateMax);
			return checkedCandidates;
		}
		return checkedCandidates;
	}
}
