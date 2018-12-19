package org.reactome.orthoinference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.schema.InvalidAttributeException;

public class ProteinCountUtility {
	
	private static Map<String, String[]> homologueMappings = new HashMap<String,String[]>();
	
	/** This function is meant to emulate the count_distinct_proteins function found in infer_events.pl.
	 A crucial note is that the Perl version seems to be depend on the order by which instance groups are taken from the DB. Often the DB IDs are ordered smallest to largest, 
	 but other times it is a consistent yet 'random' order. What that means is that every time the Perl version will pull the instances out in the exact same order, but there 
	 isn't a clear pattern (such as DB ID order) that is followed. Since this happens as well with the Java code, sometimes the protein counts will differ from the Perl protein 
	 counts. The vast majority of the time this isn't true, but this still suggests the protein count functionality should be re-written, for consistency's sake. 
	 See the bottom of ProteinCount.checkCandidates for further elaboration. 
	*/
	
	public static List<Integer> getDistinctProteinCounts (GKInstance instanceToBeInferred) throws InvalidAttributeException, Exception
	{
		// Perform an AttributeQueryRequest with specified input attributes (ReactionlikeEvent, CatalystActivity, Complex, Polymer, EWAS) and output attributes (ReferenceGeneProduct, EntitySet).
		List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
		classesToFollow.add(new ClassAttributeFollowingInstruction(ReactionlikeEvent, new String[]{input, output, catalystActivity}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(CatalystActivity, new String[]{physicalEntity}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(Complex, new String[]{hasComponent}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(Polymer, new String[]{repeatedUnit}, new String[]{}));
		classesToFollow.add(new ClassAttributeFollowingInstruction(EntityWithAccessionedSequence, new String[]{referenceEntity}, new String[]{}));
		
		String[] outClasses = new String[] {ReferenceGeneProduct, EntitySet};
		@SuppressWarnings("unchecked")
		Collection<GKInstance> followedInstances = InstanceUtilities.followInstanceAttributes(instanceToBeInferred, classesToFollow, outClasses);	
		
		// Sort instances by DB ID
		List<Long> dbIds = new ArrayList<Long>();
		Collection<GKInstance> sortedFollowedInstances = new ArrayList<GKInstance>();
		Map<Long,GKInstance> instances = new HashMap<Long,GKInstance>();
		for (GKInstance instance : followedInstances) 
		{
			dbIds.add(instance.getDBID());
			instances.put(instance.getDBID(), instance);
		}
		Collections.sort(dbIds);
		for (Long id : dbIds) 
		{
			sortedFollowedInstances.add(instances.get(id));
		}
		
		// With the output instances saved in followedInstances, begin the protein count process, which is based on the homologue mappings (orthopairs) files.
		List<Integer> distinctProteinCounts = new ArrayList<Integer>();
		int total = 0;
		int inferrable = 0;
		int max = 0;
		// If it is a ReferenceGene Product, the inferrable and max values are incremented depending on the number of homologue mappings, while total is incremented for each entity. 
		for (GKInstance entityInst : sortedFollowedInstances)
		{
			if (entityInst.getSchemClass().isa(ReferenceGeneProduct))
			{
				int count = 0;
				String identifierName = entityInst.getAttributeValue(identifier).toString();
				if (homologueMappings.get(identifierName) != null)
				{
					count = homologueMappings.get(identifierName).length;
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
		for (GKInstance entityInst : sortedFollowedInstances)
		{
			if (entityInst.getSchemClass().isa(EntitySet))
			{
				List<ClassAttributeFollowingInstruction> entitySetsInstancesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
				entitySetsInstancesToFollow.add(new ClassAttributeFollowingInstruction(DefinedSet, new String[]{hasMember}, new String[]{}));
				entitySetsInstancesToFollow.add(new ClassAttributeFollowingInstruction(CandidateSet, new String[]{hasMember}, new String[]{}));
				entitySetsInstancesToFollow.add(new ClassAttributeFollowingInstruction(EntityWithAccessionedSequence, new String[]{referenceEntity}, new String[]{}));
				
				String[] entitySetsOutClasses = new String[] {Complex, Polymer, ReferenceSequence};
				@SuppressWarnings("unchecked")
				Collection<GKInstance> entitySetsFollowedInstances = InstanceUtilities.followInstanceAttributes(entityInst, entitySetsInstancesToFollow, entitySetsOutClasses);
				
				// Sort instances by DB ID
				List<Long> entitySetsDbIds = new ArrayList<Long>();
				Collection<GKInstance> entitySetsSortedInstances = new ArrayList<GKInstance>();
				Map<Long,GKInstance> entitySetInstances = new HashMap<Long,GKInstance>();
				for (GKInstance instance : entitySetsFollowedInstances) 
				{
					entitySetsDbIds.add(instance.getDBID());
					entitySetInstances.put(instance.getDBID(), instance);
				}
				Collections.sort(entitySetsDbIds);
				for (Long id : entitySetsDbIds) 
				{
					entitySetsSortedInstances.add(entitySetInstances.get(id));
				}
				
				if (entitySetsFollowedInstances.size() == 0 && entityInst.getSchemClass().isa(CandidateSet))
				{	
					// Protein counts are incremented depending on the number and types of candidates
					List<Integer> checkedCandidateInstances = getCandidateProteinCounts(entityInst, sortedFollowedInstances);
					if (checkedCandidateInstances.size() > 0) 
					{
						total += checkedCandidateInstances.get(0);
						if (checkedCandidateInstances.size() > 1) 
						{
							inferrable += checkedCandidateInstances.get(1);
						}
						if (checkedCandidateInstances.size() > 2) 
						{
							max += checkedCandidateInstances.get(2);
						}
					}
					continue;
				}
				if (entitySetsFollowedInstances.size() > 0)
				{
					boolean uncountedInstances = false;
					// Little trick for breaking out of a nested loop
					outerloop:
					for (GKInstance physicalEntityInst : entitySetsSortedInstances)
					{
						for (GKInstance earlyFollowedInst : sortedFollowedInstances)
						{
							if (physicalEntityInst.getAttributeValue(DB_ID) == earlyFollowedInst.getAttributeValue(DB_ID))
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
					for (GKInstance physicalEntityInst : entitySetsSortedInstances)
					{
						if (physicalEntityInst.getSchemClass().isa(Complex) || physicalEntityInst.getSchemClass().isa(Polymer))
						{
							List<Integer> complexProteinCounts = getDistinctProteinCounts(physicalEntityInst);
							if (complexProteinCounts != null)
							{
								int subTotal = complexProteinCounts.get(0);
								int subInferred = complexProteinCounts.get(1);
								int subMax = complexProteinCounts.get(2);
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
						} else if (physicalEntityInst.getSchemClass().isa(ReferenceGeneProduct)) 
						{
							flag = 1;
							String identifierName = physicalEntityInst.getAttributeValue(identifier).toString();
							int count = 0;
							if (homologueMappings.get(identifierName) != null)
							{
								count = homologueMappings.get(identifierName).length;
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
					// After going through the logic for Complexes/Polymers and ReferenceGeneProduct, the total and inferrable values are incremented by their respective flag totals.
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
	// Function that determines protein counts of CandidateSets. Incoming arguments are the candidateSet of interest, as well as the output array from the very first AttributeQueryRequest (AQR).
	// This 'output array from the first AQR' is used to prevent redundant counts, such as if a Candidate instance has already undergone a protein count.
	private static List<Integer> getCandidateProteinCounts(GKInstance candidateSetInst, Collection<GKInstance> sortedFollowedInstances) throws InvalidAttributeException, Exception
	{
		List<Integer> checkedCandidateCounts = new ArrayList<Integer>();
		if (candidateSetInst.getAttributeValue(hasCandidate) != null)
		{
			// AttributeQueryRequest for candidateSets, where the output instances are Complex, Polymer, and ReferenceSequence
			int candidateTotal = 0;
			int candidateInferrable = 0;
			int candidateMax = 0;
			int flag = 0;
			List<ClassAttributeFollowingInstruction> candidateSetInstancesToFollow = new ArrayList<ClassAttributeFollowingInstruction>();
			candidateSetInstancesToFollow.add(new ClassAttributeFollowingInstruction(CandidateSet, new String[]{hasCandidate}, new String[]{}));
			candidateSetInstancesToFollow.add(new ClassAttributeFollowingInstruction(EntityWithAccessionedSequence, new String[]{referenceEntity}, new String[]{}));
			String[] candidateSetOutClasses = new String[] {Complex, Polymer, ReferenceSequence};
			@SuppressWarnings("unchecked")
			Collection<GKInstance> candidateSetFollowedInstances = InstanceUtilities.followInstanceAttributes(candidateSetInst, candidateSetInstancesToFollow, candidateSetOutClasses);
			
			List<Long> dbIdsCandidateSet = new ArrayList<Long>();
			Collection<GKInstance> sortedCandidateSetFollowedInstances = new ArrayList<GKInstance>();
			Map<Long,GKInstance> candidateSetInstances = new HashMap<Long,GKInstance>();
			for (GKInstance instance : candidateSetFollowedInstances) 
			{
				dbIdsCandidateSet.add(instance.getDBID());
				candidateSetInstances.put(instance.getDBID(), instance);
			}
			Collections.sort(dbIdsCandidateSet);
			for (Long id : dbIdsCandidateSet) 
			{
				sortedCandidateSetFollowedInstances.add(candidateSetInstances.get(id));
			}
			
			boolean uncountedInstances = false;
			for (GKInstance physicalEntityInst : sortedCandidateSetFollowedInstances)
			{
				for (GKInstance earlyFollowedInst : sortedFollowedInstances)
				{
					if (physicalEntityInst.getAttributeValue(DB_ID) == earlyFollowedInst.getAttributeValue(DB_ID))
					{
						continue;
					}
				}
				uncountedInstances = true;
			}
			if (!uncountedInstances)
			{
				return checkedCandidateCounts;
			}
			// For instances that are Complex or Polymer, the total, inferrable and max are incremented according to the results of a recursive countDistinctProteins call.
			for (GKInstance physicalEntityInst : sortedCandidateSetFollowedInstances)
			{
				if (physicalEntityInst.getSchemClass().isa(Complex) || physicalEntityInst.getSchemClass().isa(Polymer))
				{
					List<Integer> candidateComplexCounts = getDistinctProteinCounts(physicalEntityInst);
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
					// ReferenceGeneProduct instances can only have an inferrable of 1 (So says the Perl version)
				} else if (physicalEntityInst.getSchemClass().isa(ReferenceGeneProduct))
				{
					candidateTotal = 1;
					String identifierName = physicalEntityInst.getAttributeValue(identifier).toString();
					int count = 0;
					if (homologueMappings.get(identifierName) != null)
					{
						count = homologueMappings.get(identifierName).length;
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
			// Example: If a complex of 4 PEs has an inferrable in PE 1, 2, and 4, but not in 3, then the flag wouldn't be raised (incorrect, I believe). Alternatively, 
			// if 1 doesn't have an inferrable, but 2, 3, 4 do, then the flag would be raised (correctly). The second example given exemplifies how I believe the function 
			// is meant to work, but the Perl version doesn't handle it. Currently this emulates the Perl version.
			if (flag > 0)
			{
				checkedCandidateCounts.add(candidateTotal);
				checkedCandidateCounts.add(0); // candidateInferred value is dropped, 0 is returned instead
				return checkedCandidateCounts;
			}
			checkedCandidateCounts.add(candidateTotal);
			checkedCandidateCounts.add(candidateInferrable);
			checkedCandidateCounts.add(candidateMax);
			return checkedCandidateCounts;
		}
		return checkedCandidateCounts;
	}
	
	public static void setHomologueMappingFile(Map<String, String[]> homologueMappingsCopy) throws IOException
	{
		homologueMappings = homologueMappingsCopy;
	}
	
	public static void resetVariables() 
	{
		homologueMappings = new HashMap<String,String[]>();
	}
}
