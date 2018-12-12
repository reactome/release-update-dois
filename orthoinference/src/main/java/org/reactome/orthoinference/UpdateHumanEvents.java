package org.reactome.orthoinference;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class UpdateHumanEvents {
	
	private static MySQLAdaptor dba;
	private static String dateOfRelease = "";
	private static GKInstance summationInst;
	private static GKInstance evidenceTypeInst;
	private static GKInstance instanceEdit;
	private static List<GKInstance> updatedInferrableHumanEvents = new ArrayList<GKInstance>();
	private static Map<GKInstance, GKInstance> inferredEvent = new HashMap<GKInstance,GKInstance>();
	
	// This class populates species pathways with the instances that have been inferred. This was copied heavily from the Perl, so my explanations are a little sparse here.
	@SuppressWarnings("unchecked")
	public static void updateHumanEvents(List<GKInstance> inferrableHumanEvents) throws Exception 
	{
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
		//////
		HashSet<Long> seenFilledPathway = new HashSet<Long>();
		for (GKInstance humanPathwayInst : updatedInferrableHumanEvents)
		{
			if (!seenFilledPathway.contains(humanPathwayInst.getDBID()))
			{
				if (humanPathwayInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.hasEvent))
				{
					ArrayList<GKInstance> pathwayComponents = new ArrayList<GKInstance>();
					//TODO: Map array sizes
					for (GKInstance pathwayComponent : (Collection<GKInstance>) humanPathwayInst.getAttributeValuesList(ReactomeJavaConstants.hasEvent))
					{
						if (inferredEvent.get(pathwayComponent) != null)
						{
							pathwayComponents.add(inferredEvent.get(pathwayComponent));
						}
					}
					if (inferredEvent.get(humanPathwayInst).getSchemClass().isValidAttribute(ReactomeJavaConstants.hasEvent))
					{
						for (GKInstance pathwayComponent : pathwayComponents) 
						{
								GKInstance inferredHumanPathwayInst = inferredEvent.get(humanPathwayInst);
								inferredHumanPathwayInst = InstanceUtilities.addAttributeValueIfNeccesary(inferredHumanPathwayInst, pathwayComponent, ReactomeJavaConstants.hasEvent);
								inferredEvent.remove(humanPathwayInst);
								inferredEvent.put(humanPathwayInst, inferredHumanPathwayInst);
							
						}
						dba.updateInstanceAttribute(inferredEvent.get(humanPathwayInst), ReactomeJavaConstants.hasEvent);
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
				GKInstance createdInst = (GKInstance) humanPathwayInst.getAttributeValue(ReactomeJavaConstants.created);
				if (createdInst == null || !createdInst.getDBID().toString().matches(instanceEdit.getDBID().toString())) {

					boolean modifiedExists = false;
					for (GKInstance modifiedInst : (Collection<GKInstance>) humanPathwayInst.getAttributeValuesList(ReactomeJavaConstants.modified)) {
						if (modifiedInst.getDBID().toString().matches(instanceEdit.getDBID().toString())) {
							modifiedExists = true;
						}
					}
					if (!modifiedExists) {
						humanPathwayInst.addAttributeValue(ReactomeJavaConstants.modified, instanceEdit);
						dba.updateInstanceAttribute(humanPathwayInst, ReactomeJavaConstants.modified);
					}
					seenInstanceEdit.add(humanPathwayInst.getDBID());
				}
			}
		}
	}
	
	public static void createHumanHierarchy(GKInstance inferrableInst) throws Exception
	{
		ArrayList<GKInstance> hasEventReferrals = (ArrayList<GKInstance>) inferrableInst.getReferers(ReactomeJavaConstants.hasEvent);
		if (hasEventReferrals != null && hasEventReferrals.size() > 0)
		{
			for (GKInstance hasEventReferral : hasEventReferrals)
			{
				if (inferredEvent.get(hasEventReferral) == null)
				{
					//TODO: DisplayName?
					GKInstance infHasEventReferral = InstanceUtilities.newInferredGKInstance(hasEventReferral);
					infHasEventReferral.addAttributeValue(ReactomeJavaConstants.name, hasEventReferral.getAttributeValuesList(ReactomeJavaConstants.name));
					infHasEventReferral.addAttributeValue(ReactomeJavaConstants.summation, summationInst);
					if (infHasEventReferral.getSchemClass().isValidAttribute(ReactomeJavaConstants.releaseDate)) {
						infHasEventReferral.addAttributeValue(ReactomeJavaConstants.releaseDate, dateOfRelease);
					}
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
					infHasEventReferral.setDisplayName(hasEventReferral.getDisplayName());
					inferredEvent.put(hasEventReferral, infHasEventReferral);
					dba.storeInstance(infHasEventReferral);
					
					// This was replaced with addAttributeValueIfNecessary due to a bug where a Pathway instance's 'OrthologousEvent' attribute was being replaced,
					// instead of being added to the existing array when  the script was executed from a jar (rather than from Eclipse) (Justin Cook 2018)
//					hasEventReferral.addAttributeValue(ReactomeJavaConstants.orthologousEvent, infHasEventReferral);
					hasEventReferral = InstanceUtilities.addAttributeValueIfNeccesary(hasEventReferral, infHasEventReferral, ReactomeJavaConstants.orthologousEvent);
					dba.updateInstanceAttribute(hasEventReferral, ReactomeJavaConstants.orthologousEvent);
					
					updatedInferrableHumanEvents.add(hasEventReferral);
				}
				UpdateHumanEvents.createHumanHierarchy(hasEventReferral);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public static void inferPrecedingEvents() throws InvalidAttributeException, Exception
	{
		HashSet<GKInstance> seenPrecedingEvent = new HashSet<GKInstance>();
		for (GKInstance inferrableEvent : updatedInferrableHumanEvents)
		{
			if (!seenPrecedingEvent.contains(inferrableEvent)) 
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
				seenPrecedingEvent.add(inferrableEvent);
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
	public static void setInferredEvent(Map<GKInstance,GKInstance> inferredEventCopy) throws Exception
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
