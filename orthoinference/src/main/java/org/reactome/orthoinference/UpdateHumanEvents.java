package org.reactome.orthoinference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class UpdateHumanEvents {
	
	private static MySQLAdaptor dba;
	private static GKInstance summationInst;
	private static GKInstance evidenceTypeInst;
	private static GKInstance instanceEdit;
	private static ArrayList<GKInstance> updatedInferrableHumanEvents = new ArrayList<GKInstance>();
	private static HashMap<GKInstance, GKInstance> inferredEvent = new HashMap<GKInstance,GKInstance>();
	
	@SuppressWarnings("unchecked")
	public static void updateHumanEvents(ArrayList<GKInstance> inferrableHumanEvents) throws Exception 
	{
		// TODO: Release date; %inferred_event
		updatedInferrableHumanEvents.addAll(inferrableHumanEvents);
		HashSet<Long> seenHumanHierarchy = new HashSet<Long>();
		for (GKInstance inferrableInst : inferrableHumanEvents)
		{
			if (!seenHumanHierarchy.contains(inferrableInst.getDBID()))
			{
				UpdateHumanEvents.createHumanHierarchy(inferrableInst);
				seenHumanHierarchy.add(inferrableInst.getDBID());
			}
		}
		HashSet<Long> seenFilledPathway = new HashSet<Long>();
		for (GKInstance humanPathwayInst : updatedInferrableHumanEvents)
		{
			if (!seenFilledPathway.contains(humanPathwayInst.getDBID()))
			{
				if (humanPathwayInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.hasEvent))
				{
					ArrayList<GKInstance> pathwayComponents = new ArrayList<GKInstance>();
					for (GKInstance pathwayComponent : (Collection<GKInstance>) humanPathwayInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent))
					{
						if (inferredEvent.get(pathwayComponent) != null)
						{
							pathwayComponents.add(inferredEvent.get(pathwayComponent));
						}
					}
					if (inferredEvent.get(humanPathwayInst).getSchemClass().isValidAttribute(ReactomeJavaConstants.hasEvent))
					{
						boolean updateNeeded = false;
						for (GKInstance pathwayComponent : pathwayComponents) 
						{
							if (GenerateInstance.addAttributeValueIfNeccesary(inferredEvent.get(humanPathwayInst), pathwayComponent, ReactomeJavaConstants.hasEvent))
							{
								inferredEvent.get(humanPathwayInst).addAttributeValue(ReactomeJavaConstants.hasEvent, pathwayComponent);
								updateNeeded = true;
							}
						}
						if (updateNeeded)
						{
							dba.updateInstanceAttribute(inferredEvent.get(humanPathwayInst), ReactomeJavaConstants.hasEvent);
						}
					}
				}
				seenFilledPathway.add(humanPathwayInst.getDBID());
			}
		}
		
		UpdateHumanEvents.inferPrecedingEvents();
		HashSet<Long> seenInstanceEdit = new HashSet<Long>();
		for (GKInstance humanPathwayInst : updatedInferrableHumanEvents)
		{
			if (!seenInstanceEdit.contains(humanPathwayInst.getDBID())) {
				humanPathwayInst.addAttributeValue(ReactomeJavaConstants.instanceEdit, instanceEdit);
				seenInstanceEdit.add(humanPathwayInst.getDBID());
			}
		}
	}
	
	public static void createHumanHierarchy(GKInstance inferrableInst) throws Exception
	{
//			System.out.println(inferabbleInst.getDBID());
		ArrayList<GKInstance> hasEventReferrals = (ArrayList<GKInstance>) inferrableInst.getReferers(ReactomeJavaConstants.hasEvent);
		if (hasEventReferrals != null && hasEventReferrals.size() > 0)
		{
			for (GKInstance hasEventReferral : hasEventReferrals)
			{
				if (inferredEvent.get(hasEventReferral) == null)
				{
					GKInstance infHasEventReferral = GenerateInstance.newInferredGKInstance(hasEventReferral);
					infHasEventReferral.addAttributeValue(ReactomeJavaConstants.name, hasEventReferral.getAttributeValuesList(ReactomeJavaConstants.name));
					infHasEventReferral.addAttributeValue(ReactomeJavaConstants.summation, summationInst);
					infHasEventReferral.addAttributeValue(ReactomeJavaConstants.inferredFrom, hasEventReferral);
					infHasEventReferral.addAttributeValue(ReactomeJavaConstants.evidenceType, evidenceTypeInst);
					for (GKInstance goBioProcessInst : (Collection<GKInstance>) hasEventReferral.getAttributeValuesList(ReactomeJavaConstants.goBiologicalProcess))
					{
						infHasEventReferral.addAttributeValue(ReactomeJavaConstants.goBiologicalProcess, goBioProcessInst);
					}
					infHasEventReferral.addAttributeValue(ReactomeJavaConstants.orthologousEvent, hasEventReferral);
					
					if (hasEventReferral.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
					{
						System.out.println("REACTION");
						System.exit(0);
					}
					
					inferredEvent.put(hasEventReferral, infHasEventReferral);
					dba.storeInstance(infHasEventReferral);
					hasEventReferral.addAttributeValue(ReactomeJavaConstants.orthologousEvent, infHasEventReferral);
					dba.updateInstanceAttribute(hasEventReferral, ReactomeJavaConstants.orthologousEvent);
					
					updatedInferrableHumanEvents.add(hasEventReferral);
					UpdateHumanEvents.createHumanHierarchy(hasEventReferral);
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public static void inferPrecedingEvents() throws InvalidAttributeException, Exception
	{
		//TODO: %seen_event
		for (GKInstance inferrableEvent : updatedInferrableHumanEvents)
		{
			if (inferrableEvent.getAttributeValue(ReactomeJavaConstants.precedingEvent)!= null)
			{
				ArrayList<GKInstance> precedingEventInstances = new ArrayList<GKInstance>();
				for (GKInstance precedingEvent : (Collection<GKInstance>) inferrableEvent.getAttributeValuesList(ReactomeJavaConstants.precedingEvent))
				{
					if (inferredEvent.get(precedingEvent) != null)
					{
						precedingEventInstances.add(inferredEvent.get(precedingEvent));
					}
				}
				
				HashSet<String> seen = new HashSet<String>();
				for (GKInstance precedingEvent : (Collection<GKInstance>) inferredEvent.get(inferrableEvent).getAttributeValuesList(ReactomeJavaConstants.precedingEvent))
				{
					seen.add(precedingEvent.getDBID().toString());
				}
				ArrayList<GKInstance> updatedPrecedingEventInstances = new ArrayList<GKInstance>();
				for (GKInstance precedingEvent : precedingEventInstances)
				{
					if (!seen.contains(precedingEvent.getDBID().toString()))
					{
						updatedPrecedingEventInstances.add(precedingEvent);
					}
				}
				if (updatedPrecedingEventInstances != null && updatedPrecedingEventInstances.size() > 0)
				{
					inferredEvent.get(inferrableEvent).addAttributeValue(ReactomeJavaConstants.precedingEvent, updatedPrecedingEventInstances);
					dba.updateInstanceAttribute(inferredEvent.get(inferrableEvent), ReactomeJavaConstants.precedingEvent);
				}
			}
		}
	}
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}
	public static void setSummationInst(GKInstance summationInstCopy) throws Exception
	{
		summationInst = summationInstCopy;
	}
	public static void setEvidenceTypeInst(GKInstance evidenceTypeInstCopy) throws Exception
	{
		evidenceTypeInst = evidenceTypeInstCopy;
	}
	public static void setInferredEvent(HashMap<GKInstance,GKInstance> inferredEventCopy) throws Exception
	{
		inferredEvent = inferredEventCopy;
	}
	
	public static void resetVariables()
	{
		updatedInferrableHumanEvents = new ArrayList<GKInstance>();
		inferredEvent = new HashMap<GKInstance,GKInstance>();
	}
	
	public static void setInstanceEdit(GKInstance instanceEditCopy) 
	{
		instanceEdit = instanceEditCopy;
	}
}
