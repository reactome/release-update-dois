package org.reactome.orthoinference;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class InferReaction {

	private static MySQLAdaptor dba;
	private static String dateOfRelease = "";
	private static String eligibleFilehandle;
	private static String inferredFilehandle;
	private static GKInstance summationInst;
	private static GKInstance evidenceTypeInst;
	private static Map<GKInstance, GKInstance> inferredCatalyst = new HashMap<GKInstance,GKInstance>();
	private static Map<GKInstance, GKInstance> inferredEvent = new HashMap<GKInstance,GKInstance>();
	private static Integer eligibleCount = 0;
	private static Integer inferredCount = 0;
	private static List<GKInstance> inferrableHumanEvents = new ArrayList<GKInstance>();
	
	// Infers PhysicalEntity instances of input, output, catalyst activity, and regulations that are associated with incoming reactionInst.
	public static void reactionInferrer(GKInstance reactionInst) throws InvalidAttributeException, Exception
	{
		// Checks if an instance's inference should be skipped, based on a variety of factors such as a manual skip list, if it's chimeric, etc. 
		// TODO: Log return reason
		if (SkipTests.checkIfInstanceShouldBeSkipped(reactionInst))
		{
			return;
		}
		// HashMaps are used to prevent redundant inferences.
		if (inferredEvent.get(reactionInst) == null)
		{
			///// The beginning of an inference process:
			// Creates inferred instance of reaction.
			GKInstance infReactionInst = InstanceUtilities.newInferredGKInstance(reactionInst);
			infReactionInst.addAttributeValue(name, reactionInst.getAttributeValuesList(name));
			infReactionInst.addAttributeValue(goBiologicalProcess, reactionInst.getAttributeValue(goBiologicalProcess));
			infReactionInst.addAttributeValue(summation, summationInst);
			infReactionInst.addAttributeValue(evidenceType, evidenceTypeInst);
			infReactionInst.addAttributeValue(_displayName, reactionInst.getAttributeValue(_displayName));

			// This function finds the total number of distinct proteins associated with an instance, as well as the number that can be inferred.
			// Total proteins are stored in reactionProteinCounts[0], inferrable proteins in [1], and the maximum number of homologues for any entity involved in index [2].
			// Reactions with no proteins/EWAS (Total = 0) are not inferred.
			List<Integer> reactionProteinCounts = ProteinCount.countDistinctProteins(reactionInst);
			if (reactionProteinCounts.get(0) > 0) {
				String eligibleEventName = reactionInst.getAttributeValue(DB_ID).toString() + "\t" + reactionInst.getDisplayName() + "\n";	
				// Having passed all tests/filters until now, the reaction is recorded in the 'eligible reactions' file, meaning inference is continued.
				eligibleCount++;
				Files.write(Paths.get(eligibleFilehandle), eligibleEventName.getBytes(), StandardOpenOption.APPEND);
				// Attempt to infer all PhysicalEntities associated with this reaction's Input, Output, CatalystActivity and RegulatedBy attributes.
				// Failure to successfully infer any of these attributes will end inference for this reaction.
				if (inferAttributes(reactionInst, infReactionInst, input))
				{
					if (inferAttributes(reactionInst, infReactionInst, output))
					{
						if (inferCatalysts(reactionInst, infReactionInst))
						{
							// Many reactions are not regulated at all, meaning inference is attempted but will not end the process if there is nothing to infer. 
							// The inference process will end though if inferRegulations returns an invalid value.
							List<GKInstance> inferredRegulations = inferRegulations(reactionInst);
							if (inferredRegulations.size() == 1 && inferredRegulations.get(0) == null)
							{
								return;
							}
							if (infReactionInst.getSchemClass().isValidAttribute(releaseDate)) {
								infReactionInst.addAttributeValue(releaseDate, dateOfRelease);
							}
							// FetchIdenticalInstances would just return the instance being inferred. Since this step is meant to always
							// add a new inferred instance, the storeInstance method is just called here. 
							dba.storeInstance(infReactionInst);
							if (infReactionInst.getSchemClass().isValidAttribute(inferredFrom))
							{
								infReactionInst = InstanceUtilities.addAttributeValueIfNeccesary(infReactionInst, reactionInst, inferredFrom);
								dba.updateInstanceAttribute(infReactionInst, inferredFrom);
							}
							infReactionInst = InstanceUtilities.addAttributeValueIfNeccesary(infReactionInst, reactionInst, orthologousEvent);
							dba.updateInstanceAttribute(infReactionInst, orthologousEvent);
							reactionInst.addAttributeValue(orthologousEvent, infReactionInst);
							dba.updateInstanceAttribute(reactionInst, orthologousEvent);
							
							inferredEvent.put(reactionInst, infReactionInst);
							
							// Regulations instances require the DB to contain the inferred ReactionlikeEvent, so Regulations inference happens post-inference
							if (inferredRegulations.size() > 0)
							{
								for (GKInstance infRegulation : inferredRegulations)
								{
									infRegulation = InstanceUtilities.checkForIdenticalInstances(infRegulation);
									infReactionInst.addAttributeValue("regulatedBy", infRegulation);
									dba.updateInstanceAttribute(infReactionInst, "regulatedBy");
								}
							}
							// After successfully adding a new inferred instance to the DB, it is recorded in the 'inferred reactions' file
							inferredCount++;
							inferrableHumanEvents.add(reactionInst);
							String inferredEvent = infReactionInst.getAttributeValue(DB_ID).toString() + "\t" + infReactionInst.getDisplayName() + "\n";	
							Files.write(Paths.get(inferredFilehandle), inferredEvent.getBytes(), StandardOpenOption.APPEND);
//							System.out.println(reactionInst);
						}
					}
					return;
				}
			} 
		}
	}
	
	// Function used to create inferred PhysicalEntities contained in the 'input' or 'output' attributes of the current reaction instance.
	@SuppressWarnings("unchecked")
	private static boolean inferAttributes(GKInstance reactionInst, GKInstance infReactionInst, String attribute) throws InvalidAttributeException, Exception
	{
		List<GKInstance> infAttributeInstances = new ArrayList<GKInstance>();
		for (GKInstance attributeInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(attribute))
		{
			GKInstance infAttributeInst = OrthologousEntity.createOrthoEntity(attributeInst, false);
			if (infAttributeInst == null)
			{
				return false;
			} 
			infAttributeInstances.add(infAttributeInst);
		}
		infReactionInst.addAttributeValue(attribute, infAttributeInstances);
		return true;
	}
	
	// Function used to create inferred catalysts associated with the current reaction instance.
	// Infers all PhysicalEntity's associated with the reaction's 'catalystActivity' and 'activeUnit' attributes
	@SuppressWarnings("unchecked")
	private static boolean inferCatalysts(GKInstance reactionInst, GKInstance infReactionInst) throws InvalidAttributeException, Exception
	{
		for (GKInstance catalystInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(catalystActivity))
		{
			if (inferredCatalyst.get(catalystInst) == null)
			{
				GKInstance infCatalystInst = InstanceUtilities.newInferredGKInstance(catalystInst);
				infCatalystInst.setDbAdaptor(dba);
				infCatalystInst.addAttributeValue(activity, catalystInst.getAttributeValue(activity));
				if (catalystInst.getAttributeValuesList(physicalEntity) != null)
				{
					GKInstance infCatalystPEInst = OrthologousEntity.createOrthoEntity((GKInstance) catalystInst.getAttributeValue(physicalEntity), false);
					if (infCatalystPEInst != null) 
					{
						infCatalystInst.addAttributeValue(physicalEntity, infCatalystPEInst);
					} else {
						return false;
					}
				}
				
				List<GKInstance> activeUnits = new ArrayList<GKInstance>();
				for (GKInstance activeUnitInst : (Collection<GKInstance>) catalystInst.getAttributeValuesList(activeUnit))
				{
					GKInstance infActiveUnitInst = OrthologousEntity.createOrthoEntity(activeUnitInst, false);
					if (infActiveUnitInst != null)
					{
						activeUnits.add(infActiveUnitInst);
					}
				}
				infCatalystInst.addAttributeValue(activeUnit, activeUnits);
				infCatalystInst.addAttributeValue(_displayName, catalystInst.getAttributeValue(_displayName));
				infCatalystInst = InstanceUtilities.checkForIdenticalInstances(infCatalystInst);
				inferredCatalyst.put(catalystInst, infCatalystInst);
				infReactionInst.addAttributeValue(catalystActivity, infCatalystInst);
			} 
		} 
		return true;
	}
	
	@SuppressWarnings("unchecked")
	// Function used to infer regulation instances. Logic existed for regulators that had CatalystActivity and Event instances, but they have never come up in the many times this has been run.
	private static List<GKInstance> inferRegulations(GKInstance reactionInst) throws InvalidAttributeException, Exception
	{
		List<GKInstance> inferredRegulations = new ArrayList<GKInstance>();
		for (GKInstance regulatedInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList("regulatedBy"))
		{
			GKInstance regulatorInst = (GKInstance) regulatedInst.getAttributeValue(regulator);
			GKInstance infRegulatorInst = null;
			if (regulatorInst.getSchemClass().isa(PhysicalEntity))
			{
				infRegulatorInst = OrthologousEntity.createOrthoEntity(regulatorInst, false);
			} else if (regulatorInst.getSchemClass().isa(CatalystActivity))
			{
				System.out.println("Regulation instance is a CatalystActivity, which is unexpected");
				System.exit(0);
			} else if (regulatorInst.getSchemClass().isa(Event))
			{
				System.out.println("Regulation instance is an Event, which is unexpected");
				System.exit(0);
			}
			if (infRegulatorInst == null) {
				if (regulatedInst.getSchemClass().isa(Requirement)) 
				{
					inferredRegulations.clear();
					GKInstance nullInst = null;
					inferredRegulations.add(nullInst);
					return inferredRegulations;
				} else {
					continue;
				}
			}
			GKInstance infRegulationInst = InstanceUtilities.newInferredGKInstance(regulatedInst);
			infRegulationInst.setDbAdaptor(dba);
			infRegulationInst.addAttributeValue(regulator, infRegulatorInst);
			infRegulationInst.addAttributeValue(_displayName, regulatedInst.getAttributeValue(_displayName));
			inferredRegulations.add(infRegulationInst);
		}
		return inferredRegulations;
	}
	
	public static void setReleaseDate(String dateOfReleaseCopy) 
	{
		dateOfRelease = dateOfReleaseCopy;
	}
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}
	
	public static void setEligibleFilename(String eligibleFilename)
	{
		eligibleFilehandle = eligibleFilename;
	}
	
	public static void setInferredFilename(String inferredFilename)
	{
		inferredFilehandle = inferredFilename;
	}
	
	public static void setEvidenceTypeInstance(GKInstance evidenceTypeInstCopy) throws Exception
	{
		evidenceTypeInst = evidenceTypeInstCopy;
	}
	
	public static void setSummationInstance(GKInstance summationInstCopy) throws Exception
	{
		summationInst = summationInstCopy;
	}
	
	public static Map<GKInstance, GKInstance> getInferredEvent()
	{
		return inferredEvent;
	}
	
	public static List<GKInstance> getInferrableHumanEvents()
	{
		return inferrableHumanEvents;
	}

	public static int getEligibleCount()
	{
		return eligibleCount;
	}
	
	public static int getInferredCount()
	{
		return inferredCount;
	}
	
	public static void resetVariables() 
	{
		inferredCatalyst = new HashMap<GKInstance,GKInstance>();
		inferredEvent = new HashMap<GKInstance,GKInstance>();
		eligibleCount = 0;
		inferredCount = 0;
		inferrableHumanEvents = new ArrayList<GKInstance>();
	}
}
