package org.reactome.orthoinference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
	// This function is meant to emulate the count_distinct_proteins function found in infer_events.pl.
	// A crucial note is that the Perl version seems to be depend on the order by which instance groups are taken from the DB. Often the DB IDs are ordered smallest to largest, but other times it is a consistent yet 'random' order.
	// What that means is that every time the Perl version will pull the instances out in the exact same order, but there isn't a clear pattern (such as DB ID order) that is followed. Since this happens as well with the Java code,
	// sometimes the protein counts will differ from the Perl protein counts. The vast majority of the time this isn't true, but this still suggests the protein count functionality should be re-written, for consistency's sake. 
	// See the bottom of ProteinCount.checkCandidates for further elaboration. 
	public static List<Integer> countDistinctProteins (GKInstance instanceToBeInferred) throws InvalidAttributeException, Exception
	{
		// Perform an AttributeQueryRequest with specified input attributes (ReactionlikeEvent, CatalystActivity, Complex, Polymer, EWAS) and output attributes (ReferenceGeneProduct, EntitySet).
		List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.ReactionlikeEvent, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.CatalystActivity, new String[]{ReactomeJavaConstants.physicalEntity}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Complex, new String[]{ReactomeJavaConstants.hasComponent}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Polymer, new String[]{ReactomeJavaConstants.repeatedUnit}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.EntityWithAccessionedSequence, new String[]{ReactomeJavaConstants.referenceEntity}, new String[]{}));
		
		String[] outClasses = new String[] {ReactomeJavaConstants.ReferenceGeneProduct, ReactomeJavaConstants.EntitySet};
		@SuppressWarnings("unchecked")
		Collection<GKInstance> followedInstances = InstanceUtilities.followInstanceAttributes(instanceToBeInferred, classesToFollow, outClasses);	
		
		// TODO: Proper naming of sorted functions
		ArrayList<Long> dbIds = new ArrayList<Long>();
		Collection<GKInstance> sortedInstances = new ArrayList<GKInstance>();
		HashMap<Long,GKInstance> instances = new HashMap<Long,GKInstance>();
		for (GKInstance instance : followedInstances) {
			dbIds.add(instance.getDBID());
			instances.put(instance.getDBID(), instance);
		}
		Collections.sort(dbIds);
		for (Long id : dbIds) {
			sortedInstances.add(instances.get(id));
		}
		followedInstances = sortedInstances;
		
		// With the output instances saved in followedInstances, begin the protein count process, which is based on the homologue mappings files.
		List<Integer> distinctProteinCounts = new ArrayList<Integer>();
		int total = 0;
		int inferrable = 0;
		int max = 0;
		// If it is a ReferenceGene Product, the inferrable and max values are incremented depending on the number of homologue mappings, while total is incremented for each entity. 
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
					inferrable++;
				}
			}
		}
		// For EntitySets, another AttributeQueryRequest is completed. This time the output classes are Complex, Polymer, and ReferenceSequence.
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
				
				// TODO: Proper naming of sorted functions
				ArrayList<Long> dbIds1 = new ArrayList<Long>();
				Collection<GKInstance> sortedInstances1 = new ArrayList<GKInstance>();
				HashMap<Long,GKInstance> instances1 = new HashMap<Long,GKInstance>();
				for (GKInstance instance : entitySetsFollowedInstances) {
					dbIds1.add(instance.getDBID());
					instances1.put(instance.getDBID(), instance);
				}
				Collections.sort(dbIds1);
				for (Long id : dbIds1) {
					sortedInstances1.add(instances1.get(id));
				}
				entitySetsFollowedInstances = sortedInstances1;
				
				if (entitySetsFollowedInstances.size() == 0 && entity.getSchemClass().isa(ReactomeJavaConstants.CandidateSet))
				{	
					// Protein counts are incremented depending on the number and types of candidates
					List<Integer> checkedCandidates = ProteinCount.checkCandidates(entity, followedInstances);
					if (checkedCandidates.size() > 0) {
						total += checkedCandidates.get(0);
						if (checkedCandidates.size() > 1) 
						{
							inferrable += checkedCandidates.get(1);
						}
						if (checkedCandidates.size() > 2) 
						{
							max += checkedCandidates.get(2);
						}
					}
					continue;
				}
				if (entitySetsFollowedInstances.size() > 0)
				{

					boolean uncountedInstances = false;
					outerloop:
					for (GKInstance physicalEntity : entitySetsFollowedInstances)
					{
						for (GKInstance earlyFollowedInstance : followedInstances)
						{
							if (physicalEntity.getAttributeValue(ReactomeJavaConstants.DB_ID) == earlyFollowedInstance.getAttributeValue(ReactomeJavaConstants.DB_ID))
							{
								continue outerloop;
							}
						}
						uncountedInstances = true;
					}
					if (!uncountedInstances)
					{
						continue;
					}
					// For Complexes and Polymers, the flag and flagInferred variables determine both if and by how much the values are incremented.
					// These values are determined by the result of a recursive ProteinCount call for each entity in the second followedInstances array. 
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
						} else if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.ReferenceGeneProduct)) {
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
					// After going through logic for Complexes/Polymers, ReferenceGeneProduct, the total and inferabble values are incremented by their respective flag totals.
					total += flag;
					inferrable += flagInferred;
				}
			}
		}
		distinctProteinCounts.add(total);
		distinctProteinCounts.add(inferrable);
		distinctProteinCounts.add(max);
		return distinctProteinCounts;
	}
	
	// Function that determines protein counts of CandidateSets. Incoming arguments are the candidateSet of interest, as well as the output array from the first AttributeQueryRequest.
	public static List<Integer> checkCandidates(GKInstance candidateSet, Collection<GKInstance> followedInstances) throws InvalidAttributeException, Exception
	{
		List<Integer> checkedCandidates = new ArrayList<Integer>();
		if (candidateSet.getAttributeValue(ReactomeJavaConstants.hasCandidate) != null)
		{
			// AttributeQueryRequest for candidateSets, where the output instances are Complex, Polymer, and ReferenceSequence
			int candidateTotal = 0;
			int candidateInferrable = 0;
			int candidateMax = 0;
			Integer flag = 0;
			List<ClassAttributeFollowingInstruction> candidateSetInstancesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
			candidateSetInstancesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.CandidateSet, new String[]{ReactomeJavaConstants.hasCandidate}, new String[]{}));
			candidateSetInstancesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.EntityWithAccessionedSequence, new String[]{ReactomeJavaConstants.referenceEntity}, new String[]{}));
			String[] candidateSetOutClasses = new String[] {ReactomeJavaConstants.Complex, ReactomeJavaConstants.Polymer, ReactomeJavaConstants.ReferenceSequence};
			@SuppressWarnings("unchecked")
			Collection<GKInstance> candidateSetFollowedInstances = InstanceUtilities.followInstanceAttributes(candidateSet, candidateSetInstancesToFollow, candidateSetOutClasses);
			
			// TODO: Proper naming of sorted functions
			ArrayList<Long> dbIds2 = new ArrayList<Long>();
			Collection<GKInstance> sortedInstances2 = new ArrayList<GKInstance>();
			HashMap<Long,GKInstance> instances2 = new HashMap<Long,GKInstance>();
			for (GKInstance instance : candidateSetFollowedInstances) {
				dbIds2.add(instance.getDBID());
				instances2.put(instance.getDBID(), instance);
			}
			Collections.sort(dbIds2);
			for (Long id : dbIds2) {
				sortedInstances2.add(instances2.get(id));
			}
			candidateSetFollowedInstances = sortedInstances2;
			
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
			// For instances that are Complex or Polymer, the total, inferrable and max are incremented according to the results of a recursive countDistinctProteins call.
			for (GKInstance physicalEntity : candidateSetFollowedInstances)
			{
				if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex) || physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Polymer))
				{
					List<Integer> candidateComplexCounts = ProteinCount.countDistinctProteins(physicalEntity);
					if (candidateComplexCounts.size() > 0)
					{
						if (candidateComplexCounts.get(0) > 0 && candidateComplexCounts.get(1) == 0)
						{
							flag++;
						}
						if (candidateTotal > 0 && candidateComplexCounts.get(0) > candidateTotal)
						{
							candidateTotal = candidateComplexCounts.get(0);
						}
						if (candidateInferrable > 0 && candidateComplexCounts.get(1) > candidateInferrable)
						{
							candidateInferrable = candidateComplexCounts.get(1);
						}
						if (candidateMax > 0 && candidateComplexCounts.get(2) > candidateMax)
						{
							candidateMax = candidateComplexCounts.get(2);
						}
					}
					// ReferenceGeneProduct instances can only have an inferrable of 1
				} else if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.ReferenceGeneProduct))
				{
					candidateTotal = 1;
					String identifier = physicalEntity.getAttributeValue(ReactomeJavaConstants.identifier).toString();
					int count = 0;
					if (homologueMappings.get(identifier) != null)
					{
						count = homologueMappings.get(identifier).length;
					}
					if (count > 0)
					{
						candidateInferrable = 1;
					}
					if (candidateInferrable == 0)
					{
						flag++;
					}
				}
			} 
			// This was the tricky bit between Perl and Java. The flag is meant to drop any CandidateSet counts that don't all have an inferrable protein. 
			// In the Perl version, instance order makes a big difference here, since the flag value persists through the for-loop. 
			// Example: If a complex of 4 PEs has an inferrable in PE 1, 2, and 4, but not in 3, then the flag wouldn't be raised (incorrect, I believe). Alternatively, if 1 doesn't have an inferrable, but 2, 3, 4 do, then the flag would be raised (correctly). 
			// The second example given exemplifies how I believe the function is meant to work, but the Perl version doesn't handle it. Currently this emulates the Perl version, but one of my earlier commits of this code section has it the other way (use git blame on this file).
			if (flag > 0)
			{
				checkedCandidates.add(candidateTotal);
				checkedCandidates.add(0); // candidateInferred value is dropped, 0 is returned instead
				return checkedCandidates;
			}
			checkedCandidates.add(candidateTotal);
			checkedCandidates.add(candidateInferrable);
			checkedCandidates.add(candidateMax);
			return checkedCandidates;
		}
		return checkedCandidates;
	}
	
	public static void resetVariables() 
	{
		homologueMappings = new HashMap<String,String[]>();
	}
}
