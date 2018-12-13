package org.reactome.orthoinference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;

public class OrthologousEntity {
	
	private static MySQLAdaptor dba;
	private static GKInstance instanceEdit;
	private static GKInstance complexSummationInst;
	private static GKInstance speciesInst;
	static GKInstance nullInst = null;
	private static HashMap<GKInstance, GKInstance> orthologousEntity = new HashMap<GKInstance,GKInstance>();
	private static HashMap<GKInstance, GKInstance> homolEWAS = new HashMap<GKInstance,GKInstance>();
	private static HashMap<GKInstance, GKInstance> complexPolymer = new HashMap<GKInstance, GKInstance>();
	private static HashMap<GKInstance, GKInstance> inferredGSE = new HashMap<GKInstance, GKInstance>();
	private static HashMap<String,GKInstance> definedSetIdenticals = new HashMap<String,GKInstance>();
	private static HashMap<String,GKInstance> complexIdenticals = new HashMap<String,GKInstance>();
	private static HashMap<String,GKInstance> entitySetIdenticals = new HashMap<String,GKInstance>();

/** The heart of the OrthoInference process. This function takes PhysicalEntity (PE) instances and will infer those that are EWAS', Complexes/Polymers, or EntitySets.
	 The function's arguments are an incoming PE instance and an override attribute. Instances that are comprised of PE's will often recursively call this createOrthoEntity function
	 on constituent PE's with the override attribute set to 'true'. This ensures that these PE's are inferred, despite the fact that they might not pass some filter criteria.
	 This is often handled using 'mock' instances (i.e. 'ghost instances' from Perl script), which allow a PE to be inferred without having to commit a 'real' instance to the DB.
*/
	public static GKInstance createOrthoEntity(GKInstance entityInst, boolean override) throws InvalidAttributeException, Exception
	{
		GKInstance infEntity = null;
		if (entityInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.species))
		{
			// Cache check
			if (orthologousEntity.get(entityInst) == null)
			{
				// Checks that a species attribute exists in either the current instance or in constituent instances.
				if (!SpeciesCheck.hasSpecies(entityInst))
				{
					infEntity = entityInst;
				// Will either infer an EWAS or return a mock GEE instance if needed (i.e. if override is currently 'True')
				} else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.GenomeEncodedEntity))
				{
					if (entityInst.getSchemClass().toString().contains(ReactomeJavaConstants.EntityWithAccessionedSequence))
					{
						infEntity = OrthologousEntity.createInfEWAS(entityInst, override);
					} else {
						if (override)
						{
							GKInstance mockedInst = InstanceUtilities.newMockGKInstance(entityInst);						
							return mockedInst;
						}
					}
				// Infers Complex or Polymer instances -- Will recursively call createOrthoEntity with override on its constituent PEs
				} else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.Complex) || entityInst.getSchemClass().isa(ReactomeJavaConstants.Polymer))
				{
					infEntity = OrthologousEntity.createInfComplexPolymer(entityInst, override);
				// Infers EntitySetInstances that themselves contain the species attribute (Not just constituent instances as when hasSpecies is called above),
				// returning the current instance if it doesn't.
				} else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
				{
					if (entityInst.getAttributeValue(ReactomeJavaConstants.species) != null)
					{
						infEntity = OrthologousEntity.createInfEntitySet(entityInst, override);
					} else {
						infEntity = entityInst;
					}
				// Handles SimpleEntities by returning the current instance. The idea behind this is that SimpleEntities wouldn't need
				// to be inferred since they wouldn't change between species {Note from infer_events.pl -- David Croft}.
				} else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity))
				{
					infEntity = entityInst;
				} else {
					System.out.println("Unknown PhysicalEntity class: " + entityInst.getClass());
				}
				if (override)
				{
					return infEntity;
				}
				orthologousEntity.put(entityInst, infEntity);
			} 
			return orthologousEntity.get(entityInst);
		} else {
			// This used to have a conditional statement based on the returned value of the 'check_intracellular' function.
			// That function doesn't exist anymore (only seemed to apply to the 'mtub' species, which hasn't been inferred for a while).
			return entityInst;
		}
	}
	
	// Function that first tries to infer any EWAS' associated with the instance. For those that have more than 1, it's re-structured to a DefinedSet instance.
	// If there is no EWAS instances inferred, it will either return null or, if override is set, return a mock instance. 
	public static GKInstance createInfEWAS(GKInstance ewasInst, boolean override) throws InvalidAttributeException, Exception
	{
		if (homolEWAS.get(ewasInst) == null)
		{
			// Attempt to infer the EWAS 
			ArrayList<GKInstance> infEWASInstances = InferEWAS.ewasInferrer(ewasInst);
			// If number of EWAS instances is greater than 1, then it is considered a DefinedSet. A new inferred instance with definedSet class is created.
			if (infEWASInstances.size() > 1)
			{	
				SchemaClass definedSetClass = dba.getSchema().getClassByName(ReactomeJavaConstants.DefinedSet);
				GKInstance definedSetInst = new GKInstance(definedSetClass);
				definedSetInst.setDbAdaptor(dba);
				definedSetInst.addAttributeValue(ReactomeJavaConstants.created, instanceEdit);
				String definedSetName = "Homologues of " + ewasInst.getAttributeValue(ReactomeJavaConstants.name);
				definedSetInst.addAttributeValue(ReactomeJavaConstants.name, definedSetName);
				
				GKInstance compartmentInstGk = (GKInstance) ewasInst.getAttributeValue(ReactomeJavaConstants.compartment);
				if (compartmentInstGk.getSchemClass().isa(ReactomeJavaConstants.Compartment)) {
					definedSetInst.addAttributeValue(ReactomeJavaConstants.compartment, ewasInst.getAttributeValue(ReactomeJavaConstants.compartment));
				} else {
					GKInstance newCompartmentInst = InstanceUtilities.newCompartmentInstance(compartmentInstGk);
					definedSetInst.addAttributeValue(ReactomeJavaConstants.compartment, newCompartmentInst);
				}
				
				definedSetInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
				definedSetInst.addAttributeValue(ReactomeJavaConstants.hasMember, infEWASInstances);
				String displayName = (String) definedSetInst.getAttributeValue(ReactomeJavaConstants.name) + " [" +((GKInstance) ewasInst.getAttributeValue(ReactomeJavaConstants.compartment)).getDisplayName() + "]";
				definedSetInst.setAttributeValue(ReactomeJavaConstants._displayName, displayName);
				// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which is slow.
				String cacheKey = InstanceUtilities.getCacheKey((GKSchemaClass) definedSetInst.getSchemClass(), definedSetInst);
				if (definedSetIdenticals.get(cacheKey) != null)
				{
					definedSetInst = definedSetIdenticals.get(cacheKey);
				} else {
					definedSetInst = InstanceUtilities.checkForIdenticalInstances(definedSetInst);
					definedSetIdenticals.put(cacheKey, definedSetInst);
				}
				definedSetInst = InstanceUtilities.addAttributeValueIfNecessary(definedSetInst, ewasInst, ReactomeJavaConstants.inferredFrom);
				dba.updateInstanceAttribute(definedSetInst, ReactomeJavaConstants.inferredFrom);
				ewasInst = InstanceUtilities.addAttributeValueIfNecessary(ewasInst, definedSetInst, ReactomeJavaConstants.inferredTo);
				dba.updateInstanceAttribute(ewasInst, ReactomeJavaConstants.inferredTo);
				homolEWAS.put(ewasInst, definedSetInst);
			} else if (infEWASInstances.size() == 1)
			{
				homolEWAS.put(ewasInst, infEWASInstances.get(0));
			} else {
				if (override) {
					return InstanceUtilities.newMockGKInstance(ewasInst);
				} else {
					return nullInst;
				}
			}
		}
		return homolEWAS.get(ewasInst);
	}
	// Infers Complex or Polymer instances. These instances are generally comprised of more than 1 PhysicalEntity, and thus calls 'createOrthoEntity' for each one. Complex/Polymer instances
	// are also subject to the 'countDistinctProteins' function. The result from this needs to have at least 75% of total proteins be inferrable for orthoinference to continue. 
	public static GKInstance createInfComplexPolymer(GKInstance complexInst, boolean override) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		if (complexPolymer.get(complexInst) == null)
		{
			List<Integer> complexProteinCounts = ProteinCount.countDistinctProteins(complexInst);
			int complexTotal = complexProteinCounts.get(0);
			int complexInferrable = complexProteinCounts.get(1);
//			int complexMax = complexProteinCounts.get(2); // Doesn't get used, since MaxHomologue isn't a valid attribute anymore.
			// Filtering based on results of ProteinCounts and threshold (currently hard-coded at 75%).
			int percent = 0;
			if (complexTotal > 0)
			{
				percent = (complexInferrable * 100)/complexTotal;
			}
			if (!override)
			{
				if ((complexTotal > 0 && complexInferrable == 0) || percent < 75)
				{
					return nullInst;
				}
			}
			
			GKInstance infComplexInst = InstanceUtilities.newInferredGKInstance(complexInst);
			infComplexInst.addAttributeValue(ReactomeJavaConstants.summation, complexSummationInst);
			infComplexInst.addAttributeValue(ReactomeJavaConstants.name, complexInst.getAttributeValue(ReactomeJavaConstants.name));
			ArrayList<GKInstance> infComponents = new ArrayList<GKInstance>();
			// Inference handling is different depending on if it is a Complex or a Polymer. Complexes will infer all 'components' while Polymers will infer all 'repeatedUnits'.
			if (complexInst.getSchemClass().isa(ReactomeJavaConstants.Complex))
			{
				for (Object componentInst : complexInst.getAttributeValuesList(ReactomeJavaConstants.hasComponent))
				{	
					infComponents.add(OrthologousEntity.createOrthoEntity((GKInstance) componentInst, true));
				}
				infComplexInst.addAttributeValue(ReactomeJavaConstants.hasComponent, infComponents);
			} else {
				for (Object repeatedUnitInst : complexInst.getAttributeValuesList(ReactomeJavaConstants.repeatedUnit))
				{		
					infComponents.add(OrthologousEntity.createOrthoEntity((GKInstance) repeatedUnitInst, true));
				}
				infComplexInst.addAttributeValue(ReactomeJavaConstants.repeatedUnit, infComponents);
			}
			infComplexInst.setAttributeValue(ReactomeJavaConstants._displayName, complexInst.getAttributeValue(ReactomeJavaConstants._displayName));
			
			// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which is slow.
			String cacheKey = InstanceUtilities.getCacheKey((GKSchemaClass) infComplexInst.getSchemClass(), infComplexInst);
			if (complexIdenticals.get(cacheKey) != null)
			{
				infComplexInst = complexIdenticals.get(cacheKey);
			} else {
				infComplexInst = InstanceUtilities.checkForIdenticalInstances(infComplexInst);
				complexIdenticals.put(cacheKey, infComplexInst);
			}

			if (infComplexInst.getDBID() != complexInst.getDBID())
			{
				infComplexInst = InstanceUtilities.addAttributeValueIfNecessary(infComplexInst, complexInst, ReactomeJavaConstants.inferredFrom);
				dba.updateInstanceAttribute(infComplexInst, ReactomeJavaConstants.inferredFrom);
				complexInst = InstanceUtilities.addAttributeValueIfNecessary(complexInst, infComplexInst, ReactomeJavaConstants.inferredTo);
				dba.updateInstanceAttribute(complexInst, ReactomeJavaConstants.inferredTo);
			}
			
			if (override)
			{
				return infComplexInst;
			} 
			complexPolymer.put(complexInst, infComplexInst);
		}
		return complexPolymer.get(complexInst);
	}
	
	// EntitySet inference function. This function will initially call createOrthoEntity on all 'members' before filtering by the type of EntitySet (Open, Candidate, or Defined Sets) and completing a specific inference.
	// Important to note is that while there are multiple cases where createOrthoEntity is called (for members and candidates) in createInfEntitySet, the override functionality is not used here. 
	// Presumably, this is because the instances aren't a constituent part of a single instance (as in Complexes), but rather are stand-alone ones that also happen to be included in a Set. 
	// This means they should be subject  to the stringency of a typical instance, rather then using override to create mock instances that allow an instance to be inferred more easily. TODO: Verify these statements.
	@SuppressWarnings("unchecked")
	public static GKInstance createInfEntitySet(GKInstance entitySetInst, boolean override) throws InvalidAttributeException, Exception
	{
		if (inferredGSE.get(entitySetInst) == null)
		{
			// Equivalent to infer_members function in infer_events.pl
			HashSet<String> existingMembers = new HashSet<String>();
			ArrayList<GKInstance> membersList = new ArrayList<GKInstance>();
			for (Object memberInst : entitySetInst.getAttributeValuesList(ReactomeJavaConstants.hasMember))
			{
				GKInstance infMember = OrthologousEntity.createOrthoEntity((GKInstance) memberInst, false);
				if (infMember != null && !existingMembers.contains(infMember.getAttributeValue(ReactomeJavaConstants.name).toString()))
				{
					existingMembers.add(infMember.getAttributeValue(ReactomeJavaConstants.name).toString());
					membersList.add(infMember);
				}
			}
			// Begin inference of EntitySet
			GKInstance infEntitySetInst = InstanceUtilities.newInferredGKInstance(entitySetInst);
			infEntitySetInst.addAttributeValue(ReactomeJavaConstants.name, entitySetInst.getAttributeValuesList(ReactomeJavaConstants.name));
			infEntitySetInst.addAttributeValue(ReactomeJavaConstants.hasMember, membersList);
			// Begin specific inference process for each type of DefinedSet entity.
			if (entitySetInst.getSchemClass().isa(ReactomeJavaConstants.OpenSet))
			{
				for (GKInstance referenceEntity : (Collection<GKInstance>) entitySetInst.getAttributeValuesList(ReactomeJavaConstants.referenceEntity))
				{
					infEntitySetInst.addAttributeValue(ReactomeJavaConstants.referenceEntity, referenceEntity);
				}
			} else {
				List<Integer> entitySetProteinCounts = ProteinCount.countDistinctProteins(entitySetInst);
				int entitySetTotal = entitySetProteinCounts.get(0);
				int entitySetInferrable = entitySetProteinCounts.get(1);
//				int entitySetMax = entitySetProteinCounts.get(2);  // Doesn't get used, since MaxHomologue isn't a valid attribute anymore
				
				// Filtering based on ProteinCount results
				if (!override && entitySetTotal > 0 && entitySetInferrable == 0)
				{
					return nullInst;
				}
				
				if (entitySetInst.getSchemClass().isa(ReactomeJavaConstants.CandidateSet))
				{
					HashSet<String> existingCandidates = new HashSet<String>();
					ArrayList<GKInstance> candidatesList = new ArrayList<GKInstance>();
					// Equivalent to infer_members function in infer_events.pl
					for (Object candidateInst : entitySetInst.getAttributeValuesList(ReactomeJavaConstants.hasCandidate))
					{
						GKInstance infCandidate = OrthologousEntity.createOrthoEntity((GKInstance) candidateInst, false);
						if (infCandidate != null && !existingMembers.contains(infCandidate.getAttributeValue(ReactomeJavaConstants.name).toString()) && !existingCandidates.contains(infCandidate.getAttributeValue(ReactomeJavaConstants.name).toString()))
						{
							existingCandidates.add(infCandidate.getAttributeValue(ReactomeJavaConstants.name).toString());
							candidatesList.add(infCandidate);
						}
					}
					// Handling of CandidateSets
					if (candidatesList.size() > 0)
					{
						infEntitySetInst.addAttributeValue(ReactomeJavaConstants.hasCandidate, candidatesList);
					} else {
						if (membersList.size() != 0)
						{
							if (membersList.size() == 1)
							{
								infEntitySetInst = membersList.get(0);
							} else {
								// TODO: Add Species and Created attributes
								SchemaClass definedSetClass = dba.getSchema().getClassByName(ReactomeJavaConstants.DefinedSet);
								GKInstance definedSetInst = new GKInstance(definedSetClass);
								definedSetInst.setDbAdaptor(dba);
								definedSetInst.setAttributeValue(ReactomeJavaConstants.name, infEntitySetInst.getAttributeValuesList(ReactomeJavaConstants.name));
								definedSetInst.setAttributeValue(ReactomeJavaConstants.hasMember, membersList);
								if (entitySetInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.compartment) && entitySetInst.getAttributeValue(ReactomeJavaConstants.compartment) != null) {
									for (Object compartmentInst : entitySetInst.getAttributeValuesList(ReactomeJavaConstants.compartment)) {
										GKInstance compartmentInstGk = (GKInstance) compartmentInst;
										if (compartmentInstGk.getSchemClass().isa(ReactomeJavaConstants.Compartment)) {
											definedSetInst.addAttributeValue(ReactomeJavaConstants.compartment, compartmentInstGk);
										} else {
											GKInstance newCompartmentInst = InstanceUtilities.newCompartmentInstance(compartmentInstGk);
											definedSetInst.addAttributeValue(ReactomeJavaConstants.compartment, newCompartmentInst);
										}
									}
								}
								infEntitySetInst = definedSetInst;
							}
						} else {
							if (override)
							{
								infEntitySetInst = InstanceUtilities.newMockGKInstance(entitySetInst);
							} else {
								return nullInst;
							}
						}
					}	
				} else if (entitySetInst.getSchemClass().isa(ReactomeJavaConstants.DefinedSet))
				{
					if (membersList.size() == 0)
					{
						if (override)
						{
							return InstanceUtilities.newMockGKInstance(entitySetInst);
						} else {
							return nullInst;
						}
					} else if (membersList.size() == 1) {
						infEntitySetInst = membersList.get(0);
					}
					// If it has more than 1 member (which is the logic that would theoretically go here), nothing happens; 
					// All members are stored in this inferred instances 'hasMember' attribute near the beginning of this function.
				}
			}
			infEntitySetInst.setAttributeValue(ReactomeJavaConstants._displayName, entitySetInst.getAttributeValue(ReactomeJavaConstants._displayName));
			// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which is slow.
			String cacheKey = InstanceUtilities.getCacheKey((GKSchemaClass) infEntitySetInst.getSchemClass(), infEntitySetInst);
			if (entitySetIdenticals.get(cacheKey) != null)
			{
				infEntitySetInst = entitySetIdenticals.get(cacheKey);
			} else {
				infEntitySetInst = InstanceUtilities.checkForIdenticalInstances(infEntitySetInst);
				entitySetIdenticals.put(cacheKey, infEntitySetInst);
			}
			if (infEntitySetInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.species) && entitySetInst.getAttributeValue(ReactomeJavaConstants.species) != null)
			{
				infEntitySetInst = InstanceUtilities.addAttributeValueIfNecessary(infEntitySetInst, entitySetInst, ReactomeJavaConstants.inferredFrom);
				dba.updateInstanceAttribute(infEntitySetInst, ReactomeJavaConstants.inferredFrom);
				entitySetInst = InstanceUtilities.addAttributeValueIfNecessary(entitySetInst, infEntitySetInst, ReactomeJavaConstants.inferredTo);
				dba.updateInstanceAttribute(entitySetInst, ReactomeJavaConstants.inferredTo);
			}
			if (override)
			{
			return infEntitySetInst;
			}
			inferredGSE.put(entitySetInst, infEntitySetInst);
		}
		return inferredGSE.get(entitySetInst);
	}
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		OrthologousEntity.dba = dbAdaptor;
	}
	
	// Sets the species instance for inferEWAS to use
	public static void setSpeciesInstance(GKInstance speciesInstCopy)
	{
		speciesInst = speciesInstCopy;
	}
	public static void setComplexSummationInstance() throws Exception
	{
		complexSummationInst = new GKInstance(dba.getSchema().getClassByName(ReactomeJavaConstants.Summation));
		complexSummationInst.setDbAdaptor(dba);
		complexSummationInst.addAttributeValue(ReactomeJavaConstants.created, instanceEdit);
		String complexSummationText = "This complex/polymer has been computationally inferred (based on Ensembl Compara) from a complex/polymer involved in an event that has been demonstrated in another species.";
		complexSummationInst.addAttributeValue(ReactomeJavaConstants.text, complexSummationText);
		complexSummationInst.setAttributeValue(ReactomeJavaConstants._displayName, complexSummationText);
		complexSummationInst = InstanceUtilities.checkForIdenticalInstances(complexSummationInst);
	}
	
	public static void resetVariables()
	{
		orthologousEntity = new HashMap<GKInstance,GKInstance>();
		homolEWAS = new HashMap<GKInstance,GKInstance>();
		complexPolymer = new HashMap<GKInstance, GKInstance>();
		inferredGSE = new HashMap<GKInstance, GKInstance>();
		definedSetIdenticals = new HashMap<String,GKInstance>();
		complexIdenticals = new HashMap<String,GKInstance>();
		entitySetIdenticals = new HashMap<String,GKInstance>();
	}
	
	public static void setInstanceEdit(GKInstance instanceEditCopy) 
	{
		instanceEdit = instanceEditCopy;
	}
}
