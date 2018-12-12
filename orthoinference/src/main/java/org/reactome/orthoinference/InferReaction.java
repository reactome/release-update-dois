package org.reactome.orthoinference;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class InferReaction {

	private static MySQLAdaptor dba;
	private static String eligibleFilehandle;
	private static String inferredFilehandle;
	private static GKInstance summationInst;
	private static GKInstance evidenceTypeInst;
	private static HashMap<GKInstance, GKInstance> inferredCatalyst = new HashMap<GKInstance,GKInstance>();
	private static HashMap<GKInstance, GKInstance> inferredEvent = new HashMap<GKInstance,GKInstance>();
	private static Integer eligibleCount = 0;
	private static Integer inferredCount = 0;
	private static ArrayList<GKInstance> inferrableHumanEvents = new ArrayList<GKInstance>();
	
	private static String classFilename;
	
	// Infers PhysicalEntity instances of input, output, catalyst activity, and regulations that are associated with incoming reactionInst.
	public static void reactionInferrer(GKInstance reactionInst) throws InvalidAttributeException, Exception
	{
		// Checks if an instance's inference should be skipped, based on a variety of factors such as a manual skip list, if it's chimeric, etc. 
		if (SkipTests.skipInstance(reactionInst))
		{
			return;
		}
		// HashMaps are used to prevent redundant inferences.
		if (inferredEvent.get(reactionInst) == null)
		{
			///// The beginning of an inference process:
			// Creates inferred instance of reaction.
			GKInstance infReactionInst = InstanceUtilities.newInferredGKInstance(reactionInst);
			infReactionInst.addAttributeValue(ReactomeJavaConstants.name, reactionInst.getAttributeValuesList(ReactomeJavaConstants.name));
			infReactionInst.addAttributeValue(ReactomeJavaConstants.goBiologicalProcess, reactionInst.getAttributeValue(ReactomeJavaConstants.goBiologicalProcess));
			infReactionInst.addAttributeValue(ReactomeJavaConstants.summation, summationInst);
			infReactionInst.addAttributeValue(ReactomeJavaConstants.evidenceType, evidenceTypeInst);
			infReactionInst.addAttributeValue(ReactomeJavaConstants._displayName, reactionInst.getAttributeValue(ReactomeJavaConstants._displayName));

			// This function finds the total number of distinct proteins associated with an instance, as well as the number that can be inferred.
			// Total proteins are stored in reactionProteinCounts[0], inferrable proteins in [1], and the maximum number of homologues for any entity involved in index [2].
			// Reactions with no proteins/EWAS (Total = 0) are not inferred.
			List<Integer> reactionProteinCounts = ProteinCount.countDistinctProteins(reactionInst);
			if (reactionProteinCounts.get(0) > 0) {
				String eligibleEvent = reactionInst.getAttributeValue(ReactomeJavaConstants.DB_ID).toString() + "\t" + reactionInst.getDisplayName() + "\n";	
				// Having passed all tests/filters until now, the reaction is recorded in the 'eligible reactions' file, meaning orthoinference is continued.
				eligibleCount++;
				Files.write(Paths.get(eligibleFilehandle), eligibleEvent.getBytes(), StandardOpenOption.APPEND);
				// Attempt to infer all PhysicalEntities associated with this reaction's Input, Output, CatalystActivity and RegulatedBy attributes.
				// Failure to successfully infer any of these attributes will end orthoinference for this reaction.
				if (InferReaction.inferAttributes(reactionInst, infReactionInst, ReactomeJavaConstants.input))
				{
					if (InferReaction.inferAttributes(reactionInst, infReactionInst, ReactomeJavaConstants.output))
					{
						if (InferReaction.inferCatalyst(reactionInst, infReactionInst))
						{
							// Many reactions are not regulated at all, meaning orthoinference is attempted but will not end the process if there is nothing to infer. 
							// The orthoinference process will end though if inferRegulations returns an invalid value.
							ArrayList<GKInstance> inferredRegulations = InferReaction.inferRegulation(reactionInst);
							if (inferredRegulations.size() == 1 && inferredRegulations.get(0) == null)
							{
								return;
							}
							if (infReactionInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.releaseDate)) {
								infReactionInst.addAttributeValue(ReactomeJavaConstants.releaseDate, "2018-12-13");
							}
							// FetchIdenticalInstances would just return the instance being inferred. Since this step is meant to always
							// add a new inferred instance, the storeInstance method is just called here. 
							dba.storeInstance(infReactionInst);
							if (infReactionInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.inferredFrom))
							{
								infReactionInst = InstanceUtilities.addAttributeValueIfNeccesary(infReactionInst, reactionInst, ReactomeJavaConstants.inferredFrom);
								dba.updateInstanceAttribute(infReactionInst, ReactomeJavaConstants.inferredFrom);
							}
							infReactionInst = InstanceUtilities.addAttributeValueIfNeccesary(infReactionInst, reactionInst, ReactomeJavaConstants.orthologousEvent);
							dba.updateInstanceAttribute(infReactionInst, ReactomeJavaConstants.orthologousEvent);
							reactionInst.addAttributeValue(ReactomeJavaConstants.orthologousEvent, infReactionInst);
							dba.updateInstanceAttribute(reactionInst, ReactomeJavaConstants.orthologousEvent);
							
							inferredEvent.put(reactionInst, infReactionInst);
							// Regulations instances require the DB to contain the orthoinferred reaction
							if (inferredRegulations.size() > 0)
							{
								for (GKInstance infRegulation : inferredRegulations)
								{
									infRegulation = InstanceUtilities.checkForIdenticalInstances(infRegulation);
									infReactionInst.addAttributeValue("regulatedBy", infRegulation);
									dba.updateInstanceAttribute(infReactionInst, "regulatedBy");
								}
							}
							// After successfully adding a new orthoinferred instance to the DB, it is recorded in the 'inferred reactions' file
							inferredCount++;
							inferrableHumanEvents.add(reactionInst);
							String inferredEvent = infReactionInst.getAttributeValue(ReactomeJavaConstants.DB_ID).toString() + "\t" + infReactionInst.getDisplayName() + "\n";	
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
	public static boolean inferAttributes(GKInstance reactionInst, GKInstance infReactionInst, String attribute) throws InvalidAttributeException, Exception
	{
		ArrayList<GKInstance> infAttributeInstances = new ArrayList<GKInstance>();
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
	public static boolean inferCatalyst(GKInstance reactionInst, GKInstance infReactionInst) throws InvalidAttributeException, Exception
	{
		for (GKInstance catalystInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity))
		{
			if (inferredCatalyst.get(catalystInst) == null)
			{
				GKInstance infCatalystInst = InstanceUtilities.newInferredGKInstance(catalystInst);
				infCatalystInst.setDbAdaptor(dba);
				infCatalystInst.addAttributeValue(ReactomeJavaConstants.activity, catalystInst.getAttributeValue(ReactomeJavaConstants.activity));
				if (catalystInst.getAttributeValuesList(ReactomeJavaConstants.physicalEntity) != null)
				{
					GKInstance infCatalystPEInst = OrthologousEntity.createOrthoEntity((GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity), false);
					if (infCatalystPEInst != null) 
					{
						infCatalystInst.addAttributeValue(ReactomeJavaConstants.physicalEntity, infCatalystPEInst);
					} else {
						return false;
					}
				}
				
				ArrayList<GKInstance> activeUnits = new ArrayList<GKInstance>();
				for (GKInstance activeUnitInst : (Collection<GKInstance>) catalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit))
				{
					GKInstance infActiveUnitInst = OrthologousEntity.createOrthoEntity(activeUnitInst, false);
					if (infActiveUnitInst != null)
					{
						activeUnits.add(infActiveUnitInst);
					}
				}
				infCatalystInst.addAttributeValue(ReactomeJavaConstants.activeUnit, activeUnits);
				infCatalystInst.addAttributeValue(ReactomeJavaConstants._displayName, catalystInst.getAttributeValue(ReactomeJavaConstants._displayName));
				infCatalystInst = InstanceUtilities.checkForIdenticalInstances(infCatalystInst);
				inferredCatalyst.put(catalystInst, infCatalystInst);
				infReactionInst.addAttributeValue(ReactomeJavaConstants.catalystActivity, infCatalystInst);
			} 
		} 
		return true;
	}
	
	@SuppressWarnings("unchecked")
	// Function used to infer regulation instances. Logic existed for regulators that had CatalystActivity and Event instances, but they have never come up in the many times this has been run.
	public static ArrayList<GKInstance> inferRegulation(GKInstance reactionInst) throws InvalidAttributeException, Exception
	{
		ArrayList<GKInstance> inferredRegulations = new ArrayList<GKInstance>();
		for (GKInstance regulatedInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList("regulatedBy"))
		{
			GKInstance regulatorInst = (GKInstance) regulatedInst.getAttributeValue(ReactomeJavaConstants.regulator);
			GKInstance infRegulatorInst = null;
			if (regulatorInst.getSchemClass().isa(ReactomeJavaConstants.PhysicalEntity))
			{
				infRegulatorInst = OrthologousEntity.createOrthoEntity(regulatorInst, false);
			} else if (regulatorInst.getSchemClass().isa(ReactomeJavaConstants.CatalystActivity))
			{
				System.out.println("CA");
				System.exit(0);
			} else if (regulatorInst.getSchemClass().isa(ReactomeJavaConstants.Event))
			{
				System.out.println("EV");
				System.exit(0);
			}
			if (infRegulatorInst == null) {
				if (regulatedInst.getSchemClass().isa(ReactomeJavaConstants.Requirement)) 
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
			infRegulationInst.addAttributeValue(ReactomeJavaConstants.regulator, infRegulatorInst);
			infRegulationInst.addAttributeValue(ReactomeJavaConstants._displayName, regulatedInst.getAttributeValue(ReactomeJavaConstants._displayName));
			inferredRegulations.add(infRegulationInst);
		}
		return inferredRegulations;
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
	
	public static ArrayList<GKInstance> getInferrableHumanEvents()
	{
		return inferrableHumanEvents;
	}
	
	public static HashMap<GKInstance, GKInstance> getInferredEvent()
	{
		return inferredEvent;
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
