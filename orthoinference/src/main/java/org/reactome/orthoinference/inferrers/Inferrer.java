package org.reactome.orthoinference.inferrers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.reactome.orthoinference.cache.OrthoinferenceCache;

public interface Inferrer
{
	/**
	 * Holds three numbers: max, total, inferred.
	 * The original Perl code would return a set of three variables (which would get converted to an array) like this:<pre>
	return ($total, $inferred, $max);
	 * </pre>So this class exists to mimic that structure, until a better way to write this is found.
	 * @author sshorser
	 *
	 */
	public class SetOfInferredCounts
	{
		private int max;
		private int total;
		private int inferred;
		public int getMax()
		{
			return max;
		}
		public void setMax(int max)
		{
			this.max = max;
		}
		public int getTotal()
		{
			return total;
		}
		public void setTotal(int total)
		{
			this.total = total;
		}
		public int getInferred()
		{
			return inferred;
		}
		public void setInferred(int inferred)
		{
			this.inferred = inferred;
		}
	}
	
	/**
	 * Infers from a source event.
	 * @param sourceEvent
	 * @return
	 */
	public GKInstance infer(GKInstance sourceEvent);
	
	/**
	 * Counts distinct proteins on an instance.
	 * @param instance - the instance to count proteins for.
	 * @param adaptor
	 */
	default public SetOfInferredCounts countProteins(GKInstance instance, MySQLAdaptor adaptor)
	{
		int max = 0;
		int total = 0;
		int inferred = 0;
		SetOfInferredCounts counts = new SetOfInferredCounts();
		//The Perl form of this is a bit convoluted. This will probably end up being longer, but hopefully clearer.
		
		List<String> attributesToLookup = null;
		
		switch (instance.getSchemClass().getName())
		{
			case "ReactionlikeEvent":
				attributesToLookup = Arrays.asList("input","output","catalystActivity");
				break;
			case "CatalystActivity":
				attributesToLookup = Arrays.asList("physicalEntity");
				break;
			case "Complex":
				attributesToLookup = Arrays.asList("hasComponents");
				break;
			case "Polymer":
				attributesToLookup = Arrays.asList("repeatedUnit");
				break;
			case "EntityWithAccessionedSequence":
				attributesToLookup = Arrays.asList("referenceEntity");
				break;
			default:
				// TODO: Log a message about an unexpected class, and abort this function, maybe by throwing a new RuntimeException.
				break;
		}
		
		List<GKInstance> entities = new ArrayList<GKInstance>();
		// Get attributes one at a time, and then add them to attributeValues
		for (String attributeName : attributesToLookup)
		{
			try
			{
				entities.addAll( instance.getAttributeValuesList(attributeName) );
			}
			catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// Will need to filter output objects to keep only ReferenceGeneProduct or EntitySet
		for (GKInstance physicalEntity : entities)
		{
			if (physicalEntity.getSchemClass().isa("ReferenceGeneProduct"))
			{
				int count = 0;
				if (OrthoinferenceCache.getHomologues().containsKey(physicalEntity.getDBID().toString()))
				{
					count = OrthoinferenceCache.getHomologues().get(physicalEntity.getDBID().toString()).size();
					total ++;
					if (count > max)
					{
						max = count;
					}
					if (count > 0)
					{
						inferred ++;
					}
				}
			}
			else if (physicalEntity.getSchemClass().isa("EntitySet"))
			{
				// now need to do a lookup again on the attributes of the EntitySet, depending on whether or not it is a CandidateSet, DefinedSet, or EntityWithAccessionedSequences
				// Output should be filtered to only contain Complex-, Polymer-, or ReferenceSequence-type objects.
				try
				{
					List<GKInstance> membersOrRefEntities = new ArrayList<GKInstance>();
					switch (physicalEntity.getSchemClass().getName())
					{
						case "CandidateSet":
						case "DefinedSet":
							membersOrRefEntities.addAll( physicalEntity.getAttributeValuesList("hasMember") );
							break;
						case "EntityWithAccessionedSequence":
							membersOrRefEntities.addAll( physicalEntity.getAttributeValuesList("referenceEntity") );
							break;
						default:
							// TODO: Log a message about an unexpected class, but probably shouldn't abort the loop.
							break;
					}
					if (membersOrRefEntities.size() > 0)
					{
						// int uncounted = 0;
						
						
						int localMax = 0;
						int localInferred = 0;
						int localCount = 0;
						for (GKInstance memberOrRefEntity : membersOrRefEntities)
						{
							if (!entities.contains(memberOrRefEntity)) // TODO: this will probably not work, will need to search by DB ID.
							{
								if (memberOrRefEntity.getSchemClass().getName().equals("Complex") || memberOrRefEntity.getSchemClass().getName().equals("Polymer"))
								{
									// recursive call to this function on memberOrRefEntity
									SetOfInferredCounts localCounts = countProteins(memberOrRefEntity, adaptor);
									// TODO: Make sure the logic matches in infer_events.pl - not 100% sure about it right now...
									if (localCounts.getTotal() > localMax)
									{
										localMax = localCounts.getTotal();
									}
									if (localCounts.getTotal() > localInferred)
									{
										localInferred = localCounts.getTotal();
									}
									if (localCounts.getMax() > max)
									{
										max = localCounts.getMax();
									}
								}
								else if (memberOrRefEntity.getSchemClass().getName().equals("ReferenceGeneProduct"))
								{
									localMax = 1;
									int count = 0;
									if (OrthoinferenceCache.getHomologues().containsKey(physicalEntity.getDBID().toString()))
									{
										count = OrthoinferenceCache.getHomologues().get(physicalEntity.getDBID().toString()).size();
										total ++;
										if (count > max)
										{
											max = count;
										}
										if (count > 0)
										{
											localInferred ++;
										}
									}
								}
								//else
								//{
									// TODO: Maybe log a message about unexpected schema class here?
								//}
							}
						}
						total += localMax;
						inferred += localInferred;
					}
					else if ( (membersOrRefEntities == null || membersOrRefEntities.size()== 0) && physicalEntity.getSchemClass().isa("CandidateSet") )
					{
						checkCandidates(physicalEntity, entities, adaptor);
					}
					
				}
				catch (InvalidAttributeException e)
				{
					e.printStackTrace();
				}
				catch (Exception e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
			else
			{
				// TODO: log error message: "unknown schema class! physicalEntity.getSchemClass() Only ReferenceGeneProduct and EntitySet were expected!"
			}
		}
		counts.setInferred(inferred);
		counts.setMax(max);
		counts.setTotal(total);
		return counts;
	}
	
	public default SetOfInferredCounts checkCandidates(GKInstance entity, List<GKInstance> entities, MySQLAdaptor adaptor) throws InvalidAttributeException, Exception
	{
		SetOfInferredCounts counts = new SetOfInferredCounts();
		if (entity.getSchemClass().isValidAttribute("hasCandidate"))
		{
			List<GKInstance> referenceGeneProducts = new ArrayList<GKInstance>();
			switch (entity.getSchemClass().getName())
			{
				case "CandidateSet":
					referenceGeneProducts.addAll(entity.getAttributeValuesList("hasCandidate"));
					break;
				case "EntityWithAccessionedSequence":
					referenceGeneProducts.addAll(entity.getAttributeValuesList("referenceEntity"));
					break;
				default:
					// TODO: Error message on unexpected class.
					break;
			}
			int uncounted = 0;
			for (GKInstance referenceGeneProduct : referenceGeneProducts)
			{
				// Only proceed if the current referenceGeneProduct is not in the input list of entities which have already been counted.
				if (!entities.contains(referenceGeneProduct)) // TODO: this will probably not work, will need to search by DB ID.
				{
					if (referenceGeneProduct.getSchemClass().isa("Complex") || referenceGeneProduct.getSchemClass().isa("Polymer"))
					{
						// call back to regular countProteins method.
						SetOfInferredCounts localCounts = this.countProteins(entity, adaptor);
						
					}
					else if (referenceGeneProduct.getSchemClass().isa("ReferenceGeneProduct"))
					{
						
					}
				}
			}
		}
		return counts;
	}
}
