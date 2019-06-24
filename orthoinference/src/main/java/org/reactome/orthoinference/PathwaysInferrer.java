package org.reactome.orthoinference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;

public class PathwaysInferrer {

	private static final Logger logger = LogManager.getLogger();
	private static MySQLAdaptor dba;
	private static String dateOfRelease = "";
	private static GKInstance summationInst;
	private static GKInstance evidenceTypeInst;
	private static GKInstance instanceEditInst;
	private static List<GKInstance> updatedInferrableHumanEvents = new ArrayList<>();
	private static Map<GKInstance, GKInstance> inferredEventIdenticals = new HashMap<>();

	// This class populates species pathways with the instances that have been inferred. This was copied heavily from the Perl, so my explanations are a little sparse here.
	public static void inferPathways(List<GKInstance> inferrableHumanEvents) throws Exception
	{
		logger.info("Beginning Pathway inference");
		updatedInferrableHumanEvents.addAll(inferrableHumanEvents);

		// First, go through each of the inferred RlE instances and generate the entire pathway hierarchy it is associated with. Inferred Reactions are not
		// added to the Pathway at this point. This includes the immediate Pathway, but also all parent pathways up to its TopLevelPathway.
		logger.info("Building inferred Pathway hierarchies");
		Set<Long> seenPathwayHierarchy = new HashSet<>();
		for (GKInstance inferrableInst : inferrableHumanEvents)
		{
			logger.info("Building inferred pathways for RlE: " + inferrableInst);
			if (!seenPathwayHierarchy.contains(inferrableInst.getDBID()))
			{
				createInferredPathwayHierarchy(inferrableInst);
				seenPathwayHierarchy.add(inferrableInst.getDBID());
			} else {
				logger.info("Inferred pathways already exist for RlE");
			}
		}
		logger.info("Finished building inferred Pathway hierarchies");

		// After generating the inferred Pathways hierarchys, the associated inferred Events (RlEs & Pathways) need to be added to them.
		logger.info("Populating inferred Pathways with inferred Events");
		addInferredEventsToInferredPathways();
		logger.info("Finished populating inferred Pathways with inferred Events");

		//TODO: LOG starting HERE

		// Connect preceding events to RlEs, if they have any in the source species.
		logger.info("Adding preceding events to inferred Events");
		inferPrecedingEvents();
		logger.info("Finished adding preceding events to inferred Events");

		// Any source species Events (Pathway or RlEs) that were modified during Pathway inference are updated with a 'modified' instance edit.
		updateModifiedAttributeIfNecessary();
	}

	@SuppressWarnings("unchecked")
	// This generates the inferred Pathway of an inferred RlE. It iterates, inferring parent Pathways until reaching the TopLevelPathway.
	// Inferred Reactions are not added to the Pathway at this step.
	private static void createInferredPathwayHierarchy(GKInstance sourceEventInst) throws Exception
	{
		List<GKInstance> sourcePathwayReferralInstances = (ArrayList<GKInstance>) sourceEventInst.getReferers(hasEvent);

		if (sourcePathwayReferralInstances != null && sourcePathwayReferralInstances.size() > 0)
		{
			for (GKInstance sourcePathwayReferralInst : sourcePathwayReferralInstances)
			{
				logger.info("Generating inferred Pathway: " + sourcePathwayReferralInst);
				if (inferredEventIdenticals.get(sourcePathwayReferralInst) == null)
				{
					GKInstance infPathwayInst = InstanceUtilities.createNewInferredGKInstance(sourcePathwayReferralInst);
					infPathwayInst.addAttributeValue(name, sourcePathwayReferralInst.getAttributeValuesList(name));
					infPathwayInst.addAttributeValue(summation, summationInst);
					if (infPathwayInst.getSchemClass().isValidAttribute(releaseDate))
					{
						infPathwayInst.addAttributeValue(releaseDate, dateOfRelease);
					}
					infPathwayInst.addAttributeValue(inferredFrom, sourcePathwayReferralInst);
					infPathwayInst.addAttributeValue(evidenceType, evidenceTypeInst);
					for (GKInstance goBioProcessInst : (Collection<GKInstance>) sourcePathwayReferralInst.getAttributeValuesList(goBiologicalProcess))
					{
						infPathwayInst.addAttributeValue(goBiologicalProcess, goBioProcessInst);
					}
					infPathwayInst.addAttributeValue(orthologousEvent, sourcePathwayReferralInst);

					if (sourcePathwayReferralInst.getSchemClass().isa(ReactionlikeEvent))
					{
						logger.warn(sourcePathwayReferralInst + " is a ReactionLikeEvent, which is unexpected -- refer to infer_events.pl");
					}
					infPathwayInst.setDisplayName(sourcePathwayReferralInst.getDisplayName());
					inferredEventIdenticals.put(sourcePathwayReferralInst, infPathwayInst);
					GKInstance orthoStableIdentifierInst = EventsInferrer.getStableIdentifierGenerator().generateOrthologousStableId(infPathwayInst, sourcePathwayReferralInst);
					infPathwayInst.addAttributeValue(stableIdentifier, orthoStableIdentifierInst);
					dba.storeInstance(infPathwayInst);

					// This was replaced with addAttributeValueIfNecessary due to a bug where a Pathway instance's 'OrthologousEvent' attribute was being replaced,
					// instead of being added to the existing array when  the script was executed from a jar (rather than from Eclipse) (Justin Cook 2018)
					sourcePathwayReferralInst = InstanceUtilities.addAttributeValueIfNecessary(sourcePathwayReferralInst, infPathwayInst, orthologousEvent);
					dba.updateInstanceAttribute(sourcePathwayReferralInst, orthologousEvent);

					//TODO: At this point, sourcePathwayReferralInst is always a Pathway. Perhaps move to its own data structure? Holdout from Perl...
					updatedInferrableHumanEvents.add(sourcePathwayReferralInst);
				} else {
					logger.info("Inferred Pathway instance already exists");
				}
				createInferredPathwayHierarchy(sourcePathwayReferralInst);
			}
		} else {
			logger.info("Top Level Pathway inferred: " + sourceEventInst);
		}
	}

	// This populates the hasEvent slot of all inferred Pathways that were just generated with corresponding inferred reactions
	private static void addInferredEventsToInferredPathways() throws Exception {
		Set<Long> seenInferredPathway = new HashSet<>();
		for (GKInstance humanPathwayInst : updatedInferrableHumanEvents)
		{
			if (humanPathwayInst.getSchemClass().isValidAttribute(hasEvent)) {
				if (!seenInferredPathway.contains(humanPathwayInst.getDBID())) {
					// Collect inferred Events associated with source Event
					List<GKInstance> inferredEventInstances = new ArrayList<>();
					for (GKInstance eventInst : (Collection<GKInstance>) humanPathwayInst.getAttributeValuesList(hasEvent)) {
						if (inferredEventIdenticals.get(eventInst) != null) {
							inferredEventInstances.add(inferredEventIdenticals.get(eventInst));
						}
					}
					if (inferredEventIdenticals.get(humanPathwayInst).getSchemClass().isValidAttribute(hasEvent)) {
						// Add inferred Events to inferred Pathway
						logger.info("Adding " + inferredEventInstances.size() + " inferred Event(s) to inferred Pathway: " + inferredEventIdenticals.get(humanPathwayInst));
						for (GKInstance infEventInst : inferredEventInstances) {
							GKInstance infPathwayInst = inferredEventIdenticals.get(humanPathwayInst);
							infPathwayInst = InstanceUtilities.addAttributeValueIfNecessary(infPathwayInst, infEventInst, hasEvent);
							inferredEventIdenticals.remove(humanPathwayInst);
							inferredEventIdenticals.put(humanPathwayInst, infPathwayInst);
						}
						dba.updateInstanceAttribute(inferredEventIdenticals.get(humanPathwayInst), hasEvent);
					} else {
						logger.info(humanPathwayInst + " and " + inferredEventIdenticals.get(humanPathwayInst) + " have different classes (likely connected via manual inference");
					}
					seenInferredPathway.add(humanPathwayInst.getDBID());
				} else {
					logger.info("Inferred Pathway has already been populated with inferred Events");
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void inferPrecedingEvents() throws Exception
	{
		Set<GKInstance> seenPrecedingEvent = new HashSet<>();
		for (GKInstance inferrableEventInst : updatedInferrableHumanEvents)
		{
			if (!seenPrecedingEvent.contains(inferrableEventInst))
			{
				if (inferrableEventInst.getAttributeValue(precedingEvent)!= null)
				{
					logger.info("Adding preceding event to " + inferrableEventInst);
					List<GKInstance> precedingEventInstances = new ArrayList<>();
					// Find all preceding events for source instance that have an inferred counterpart
					for (GKInstance precedingEventInst : (Collection<GKInstance>) inferrableEventInst.getAttributeValuesList(precedingEvent))
					{
						if (inferredEventIdenticals.get(precedingEventInst) != null)
						{
							precedingEventInstances.add(inferredEventIdenticals.get(precedingEventInst));
						}
					}
					Set<String> inferredPrecedingEvents = new HashSet<>();
					// Find any inferred preceding events that already exist for the inferred instance (don't want to add any redundant preceding events)
					for (GKInstance precedingEventInst : (Collection<GKInstance>) inferredEventIdenticals.get(inferrableEventInst).getAttributeValuesList(precedingEvent))
					{
						inferredPrecedingEvents.add(precedingEventInst.getDBID().toString());
					}
					List<GKInstance> updatedPrecedingEventInstances = new ArrayList<>();
					// Find existing preceding events that haven't already been attached to the inferred instance
					for (GKInstance precedingEventInst : precedingEventInstances)
					{
						if (!inferredPrecedingEvents.contains(precedingEventInst.getDBID().toString()))
						{
							updatedPrecedingEventInstances.add(precedingEventInst);
						}
					}
					// Add preceding event to inferred instance
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

	private static void updateModifiedAttributeIfNecessary() throws Exception {

		Set<Long> seenInstanceEditInst = new HashSet<>();
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

	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}

	public static void setReleaseDate(String dateOfReleaseCopy) {
		dateOfRelease = dateOfReleaseCopy;
	}

	public static void setSummationInstance(GKInstance summationInstCopy)
	{
		summationInst = summationInstCopy;
	}

	public static void setEvidenceTypeInstance(GKInstance evidenceTypeInstCopy)
	{
		evidenceTypeInst = evidenceTypeInstCopy;
	}

	public static void setInstanceEdit(GKInstance instanceEditCopy)
	{
		instanceEditInst = instanceEditCopy;
	}

	public static void setInferredEvent(Map<GKInstance,GKInstance> inferredEventCopy)
	{
		inferredEventIdenticals = inferredEventCopy;
	}

	public static void resetVariables()
	{
		updatedInferrableHumanEvents = new ArrayList<>();
		inferredEventIdenticals = new HashMap<>();
	}
}
