package org.reactome.orthoinference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class HumanEventsUpdater {
	
	private static MySQLAdaptor dba;
	private static String dateOfRelease = "";
	private static GKInstance summationInst;
	private static GKInstance evidenceTypeInst;
	private static GKInstance instanceEditInst;
	private static List<GKInstance> updatedInferrableHumanEvents = new ArrayList<GKInstance>();
	private static Map<GKInstance, GKInstance> inferredEventIdenticals = new HashMap<GKInstance,GKInstance>();
	
	// This class populates species pathways with the instances that have been inferred. This was copied heavily from the Perl, so my explanations are a little sparse here.
	@SuppressWarnings("unchecked")
	public static void updateHumanEvents(List<GKInstance> inferrableHumanEvents) throws Exception 
	{
		updatedInferrableHumanEvents.addAll(inferrableHumanEvents);
		Set<Long> seenHumanHierarchy = new HashSet<Long>();
		for (GKInstance inferrableInst : inferrableHumanEvents)
		{
			if (!seenHumanHierarchy.contains(inferrableInst.getDBID()))
			{
				createHumanHierarchy(inferrableInst);
				seenHumanHierarchy.add(inferrableInst.getDBID());
			}
		}
		
		Set<Long> seenFilledPathway = new HashSet<Long>();
		for (GKInstance humanPathwayInst : updatedInferrableHumanEvents)
		{
			if (!seenFilledPathway.contains(humanPathwayInst.getDBID()))
			{
				if (humanPathwayInst.getSchemClass().isValidAttribute(hasEvent))
				{
					List<GKInstance> pathwayEventInstances = new ArrayList<GKInstance>();
					for (GKInstance pathwayEventInst : (Collection<GKInstance>) humanPathwayInst.getAttributeValuesList(hasEvent))
					{
						if (inferredEventIdenticals.get(pathwayEventInst) != null)
						{
							pathwayEventInstances.add(inferredEventIdenticals.get(pathwayEventInst));
						}
					}
					if (inferredEventIdenticals.get(humanPathwayInst).getSchemClass().isValidAttribute(hasEvent))
					{
						for (GKInstance pathwayEventInst : pathwayEventInstances) 
						{
								GKInstance inferredHumanPathwayInst = inferredEventIdenticals.get(humanPathwayInst);
								inferredHumanPathwayInst = InstanceUtilities.addAttributeValueIfNecessary(inferredHumanPathwayInst, pathwayEventInst, hasEvent);
								inferredEventIdenticals.remove(humanPathwayInst);
								inferredEventIdenticals.put(humanPathwayInst, inferredHumanPathwayInst);
						}
						dba.updateInstanceAttribute(inferredEventIdenticals.get(humanPathwayInst), hasEvent);
					}
				}
				seenFilledPathway.add(humanPathwayInst.getDBID());
			}
		}
		
		inferPrecedingEvents();
		
		Set<Long> seenInstanceEditInst = new HashSet<Long>();
		for (GKInstance humanPathwayInst : updatedInferrableHumanEvents)
		{
			if (!seenInstanceEditInst.contains(humanPathwayInst.getDBID())) 
			{
				GKInstance createdInst = (GKInstance) humanPathwayInst.getAttributeValue(created);
				if (createdInst == null || !createdInst.getDBID().toString().matches(instanceEditInst.getDBID().toString())) 
				{

					boolean modifiedExists = false;
					for (GKInstance modifiedInst : (Collection<GKInstance>) humanPathwayInst.getAttributeValuesList(modified)) 
					{
						if (modifiedInst.getDBID().toString().matches(instanceEditInst.getDBID().toString())) 
						{
							modifiedExists = true;
						}
					}
					if (!modifiedExists) 
					{
						humanPathwayInst.addAttributeValue(modified, instanceEditInst);
						dba.updateInstanceAttribute(humanPathwayInst, modified);
					}
					seenInstanceEditInst.add(humanPathwayInst.getDBID());
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void createHumanHierarchy(GKInstance inferrableInst) throws Exception
	{
		List<GKInstance> hasEventReferralInstances = (ArrayList<GKInstance>) inferrableInst.getReferers(hasEvent);
		if (hasEventReferralInstances != null && hasEventReferralInstances.size() > 0)
		{
			for (GKInstance hasEventReferralInst : hasEventReferralInstances)
			{
				if (inferredEventIdenticals.get(hasEventReferralInst) == null)
				{
					GKInstance infHasEventReferralInst = InstanceUtilities.createNewInferredGKInstance(hasEventReferralInst);
					infHasEventReferralInst.addAttributeValue(name, hasEventReferralInst.getAttributeValuesList(name));
					infHasEventReferralInst.addAttributeValue(summation, summationInst);
					if (infHasEventReferralInst.getSchemClass().isValidAttribute(releaseDate)) 
					{
						infHasEventReferralInst.addAttributeValue(releaseDate, dateOfRelease);
					}
					infHasEventReferralInst.addAttributeValue(inferredFrom, hasEventReferralInst);
					infHasEventReferralInst.addAttributeValue(evidenceType, evidenceTypeInst);
					for (GKInstance goBioProcessInst : (Collection<GKInstance>) hasEventReferralInst.getAttributeValuesList(goBiologicalProcess))
					{
						infHasEventReferralInst.addAttributeValue(goBiologicalProcess, goBioProcessInst);
					}
					infHasEventReferralInst.addAttributeValue(orthologousEvent, hasEventReferralInst);
					
					if (hasEventReferralInst.getSchemClass().isa(ReactionlikeEvent))
					{
						System.out.println(hasEventReferralInst + " is a ReactionLikeEvent, which is unexpected -- refer to infer_events.pl");
					}
					infHasEventReferralInst.setDisplayName(hasEventReferralInst.getDisplayName());
					inferredEventIdenticals.put(hasEventReferralInst, infHasEventReferralInst);
					dba.storeInstance(infHasEventReferralInst);
					
					// This was replaced with addAttributeValueIfNecessary due to a bug where a Pathway instance's 'OrthologousEvent' attribute was being replaced,
					// instead of being added to the existing array when  the script was executed from a jar (rather than from Eclipse) (Justin Cook 2018)
					hasEventReferralInst = InstanceUtilities.addAttributeValueIfNecessary(hasEventReferralInst, infHasEventReferralInst, orthologousEvent);
					dba.updateInstanceAttribute(hasEventReferralInst, orthologousEvent);
					
					updatedInferrableHumanEvents.add(hasEventReferralInst);
				}
				createHumanHierarchy(hasEventReferralInst);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void inferPrecedingEvents() throws InvalidAttributeException, Exception
	{
		Set<GKInstance> seenPrecedingEvent = new HashSet<GKInstance>();
		for (GKInstance inferrableEventInst : updatedInferrableHumanEvents)
		{
			if (!seenPrecedingEvent.contains(inferrableEventInst)) 
			{
				if (inferrableEventInst.getAttributeValue(precedingEvent)!= null)
				{
					List<GKInstance> precedingEventInstances = new ArrayList<GKInstance>();
					for (GKInstance precedingEventInst : (Collection<GKInstance>) inferrableEventInst.getAttributeValuesList(precedingEvent))
					{
						if (inferredEventIdenticals.get(precedingEventInst) != null)
						{
							precedingEventInstances.add(inferredEventIdenticals.get(precedingEventInst));
						}
					}
					
					Set<String> seen = new HashSet<String>();
					for (GKInstance precedingEventInst : (Collection<GKInstance>) inferredEventIdenticals.get(inferrableEventInst).getAttributeValuesList(precedingEvent))
					{
						seen.add(precedingEventInst.getDBID().toString());
					}
					List<GKInstance> updatedPrecedingEventInstances = new ArrayList<GKInstance>();
					for (GKInstance precedingEventInst : precedingEventInstances)
					{
						if (!seen.contains(precedingEventInst.getDBID().toString()))
						{
							updatedPrecedingEventInstances.add(precedingEventInst);
						}
					}
					if (updatedPrecedingEventInstances != null && updatedPrecedingEventInstances.size() > 0)
					{
						inferredEventIdenticals.get(inferrableEventInst).addAttributeValue(precedingEvent, updatedPrecedingEventInstances);
						dba.updateInstanceAttribute(inferredEventIdenticals.get(inferrableEventInst), precedingEvent);
					}
				}
				seenPrecedingEvent.add(inferrableEventInst);
			}
		}
	}
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}
	
	public static void setReleaseDate(String dateOfReleaseCopy) {
		dateOfRelease = dateOfReleaseCopy;
	}
	
	public static void setSummationInstance(GKInstance summationInstCopy) throws Exception
	{
		summationInst = summationInstCopy;
	}
	
	public static void setEvidenceTypeInstance(GKInstance evidenceTypeInstCopy) throws Exception
	{
		evidenceTypeInst = evidenceTypeInstCopy;
	}
	
	public static void setInstanceEdit(GKInstance instanceEditCopy) 
	{
		instanceEditInst = instanceEditCopy;
	}
	
	public static void setInferredEvent(Map<GKInstance,GKInstance> inferredEventCopy) throws Exception
	{
		inferredEventIdenticals = inferredEventCopy;
	}
	
	public static void resetVariables()
	{
		updatedInferrableHumanEvents = new ArrayList<GKInstance>();
		inferredEventIdenticals = new HashMap<GKInstance,GKInstance>();
	}
}
