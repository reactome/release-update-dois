package org.reactome.orthoinference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.gk.model.GKInstance;
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;

public class OrthologousEntityGenerator {
	
	private static MySQLAdaptor dba;
	private static GKInstance instanceEditInst;
	private static GKInstance complexSummationInst;
	private static GKInstance speciesInst;
	private static GKInstance nullInst = null;
	private static Map<GKInstance, GKInstance> orthologousEntityIdenticals = new HashMap<GKInstance,GKInstance>();
	private static Map<GKInstance, GKInstance> homolEWASIdenticals = new HashMap<GKInstance,GKInstance>();
	private static Map<GKInstance, GKInstance> complexPolymerIdenticals = new HashMap<GKInstance, GKInstance>();
	private static Map<GKInstance, GKInstance> inferredEntitySetIdenticals = new HashMap<GKInstance, GKInstance>();
	private static Map<String,GKInstance> definedSetIdenticals = new HashMap<String,GKInstance>();
	private static Map<String,GKInstance> complexIdenticals = new HashMap<String,GKInstance>();
	private static Map<String,GKInstance> entitySetIdenticals = new HashMap<String,GKInstance>();

/** The heart of the OrthoInference process. This function takes PhysicalEntity (PE) instances and will infer those that are EWAS', Complexes/Polymers, or EntitySets.
	 The function's arguments are an incoming PE instance and an override attribute. Instances that are comprised of PE's will often recursively call this createOrthoEntity function
	 on constituent PE's with the override attribute set to 'true'. This ensures that these PE's are inferred, despite the fact that they might not pass some filter criteria.
	 This is often handled using 'mock' instances (i.e. 'ghost instances' from Perl script), which allow a PE to be inferred without having to commit a 'real' instance to the DB.
*/
	public static GKInstance createOrthoEntity(GKInstance entityInst, boolean override) throws InvalidAttributeException, Exception
	{
		GKInstance infEntityInst = null;
		if (entityInst.getSchemClass().isValidAttribute(species))
		{
			// Cache check
			if (orthologousEntityIdenticals.get(entityInst) == null)
			{
				// Checks that a species attribute exists in either the current instance or in constituent instances.
				if (!SpeciesCheckUtility.checkForSpeciesAttribute(entityInst))
				{
					infEntityInst = entityInst;
				// Will either infer an EWAS or return a mock GEE instance if needed (i.e. if override is currently 'True')
				} else if (entityInst.getSchemClass().isa(GenomeEncodedEntity))
				{
					// TODO: Try using 'isa' here instead of contains
					if (entityInst.getSchemClass().toString().contains(EntityWithAccessionedSequence))
					{
						infEntityInst = createInfEWAS(entityInst, override);
					} else {
						if (override)
						{
							GKInstance mockedInst = InstanceUtilities.createMockGKInstance(entityInst);						
							return mockedInst;
						}
					}
				// Infers Complex or Polymer instances -- Will recursively call createOrthoEntity with override on its constituent PEs
				} else if (entityInst.getSchemClass().isa(Complex) || entityInst.getSchemClass().isa(Polymer))
				{
					infEntityInst = createInfComplexPolymer(entityInst, override);
				// Infers EntitySetInstances that themselves contain the species attribute (Not just constituent instances as when hasSpecies is called above),
				// returning the current instance if it doesn't.
				} else if (entityInst.getSchemClass().isa(EntitySet))
				{
					if (entityInst.getAttributeValue(species) != null)
					{
						infEntityInst = createInfEntitySet(entityInst, override);
					} else {
						infEntityInst = entityInst;
					}
				// Handles SimpleEntities by returning the current instance. The idea behind this is that SimpleEntities wouldn't need
				// to be inferred since they wouldn't change between species {Note from infer_events.pl -- David Croft}.
				} else if (entityInst.getSchemClass().isa(SimpleEntity))
				{
					infEntityInst = entityInst;
				} else {
					System.out.println("Unknown PhysicalEntity class: " + entityInst.getClass());
				}
				if (override)
				{
					return infEntityInst;
				}
				orthologousEntityIdenticals.put(entityInst, infEntityInst);
			} 
			return orthologousEntityIdenticals.get(entityInst);
		} else {
			// This used to have a conditional statement based on the returned value of the 'check_intracellular' function.
			// That function doesn't exist anymore (only seemed to apply to the 'mtub' species, which hasn't been inferred for a while).
			return entityInst;
		}
	}
	
	// Function that first tries to infer any EWAS' associated with the instance. For those that have more than 1 returned EWAS instance, 
	// it's re-structured to a DefinedSet instance. If there is no EWAS instances inferred, it will either return null or, if override is set, return a mock instance. 
	private static GKInstance createInfEWAS(GKInstance ewasInst, boolean override) throws InvalidAttributeException, Exception
	{
		if (homolEWASIdenticals.get(ewasInst) == null)
		{
			// Attempt to infer the EWAS 
			ArrayList<GKInstance> infEWASInstances = EWASInferrer.inferEWAS(ewasInst);
			// If number of EWAS instances is greater than 1, then it is considered a DefinedSet. A new inferred instance with definedSet class is created.
			if (infEWASInstances.size() > 1)
			{	
				SchemaClass definedSetClass = dba.getSchema().getClassByName(DefinedSet);
				GKInstance infDefinedSetInst = new GKInstance(definedSetClass);
				infDefinedSetInst.setDbAdaptor(dba);
				infDefinedSetInst.addAttributeValue(created, instanceEditInst);
				String definedSetName = "Homologues of " + ewasInst.getAttributeValue(name);
				infDefinedSetInst.addAttributeValue(name, definedSetName);
				
				GKInstance compartmentInstGk = (GKInstance) ewasInst.getAttributeValue(compartment);
				if (compartmentInstGk.getSchemClass().isa(Compartment)) {
					infDefinedSetInst.addAttributeValue(compartment, ewasInst.getAttributeValue(compartment));
				} else {
					GKInstance newCompartmentInst = InstanceUtilities.createCompartmentInstance(compartmentInstGk);
					infDefinedSetInst.addAttributeValue(compartment, newCompartmentInst);
				}
				
				infDefinedSetInst.addAttributeValue(species, speciesInst);
				infDefinedSetInst.addAttributeValue(hasMember, infEWASInstances);
				String definedSetDisplayName = (String) infDefinedSetInst.getAttributeValue(name) + " [" +((GKInstance) ewasInst.getAttributeValue(compartment)).getDisplayName() + "]";
				infDefinedSetInst.setAttributeValue(_displayName, definedSetDisplayName);
				// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which is slow.
				String cacheKey = InstanceUtilities.getCacheKey((GKSchemaClass) infDefinedSetInst.getSchemClass(), infDefinedSetInst);
				if (definedSetIdenticals.get(cacheKey) != null)
				{
					infDefinedSetInst = definedSetIdenticals.get(cacheKey);
				} else {
					infDefinedSetInst = InstanceUtilities.checkForIdenticalInstances(infDefinedSetInst);
					definedSetIdenticals.put(cacheKey, infDefinedSetInst);
				}
				infDefinedSetInst = InstanceUtilities.addAttributeValueIfNecessary(infDefinedSetInst, ewasInst, inferredFrom);
				dba.updateInstanceAttribute(infDefinedSetInst, inferredFrom);
				ewasInst = InstanceUtilities.addAttributeValueIfNecessary(ewasInst, infDefinedSetInst, inferredTo);
				dba.updateInstanceAttribute(ewasInst, inferredTo);
				homolEWASIdenticals.put(ewasInst, infDefinedSetInst);
			} else if (infEWASInstances.size() == 1)
			{
				homolEWASIdenticals.put(ewasInst, infEWASInstances.get(0));
			} else {
				if (override) 
				{
					return InstanceUtilities.createMockGKInstance(ewasInst);
				} else {
					return nullInst;
				}
			}
		}
		return homolEWASIdenticals.get(ewasInst);
	}
	// Infers Complex or Polymer instances. These instances are generally comprised of more than 1 PhysicalEntity, and calls 'createOrthoEntity' for each one. Complex/Polymer instances
	// are also subject to the 'countDistinctProteins' function. The result from this needs to have at least 75% of total proteins to be inferrable for inference to continue. 
	private static GKInstance createInfComplexPolymer(GKInstance complexInst, boolean override) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		if (complexPolymerIdenticals.get(complexInst) == null)
		{
			List<Integer> complexProteinCounts = ProteinCountUtility.getDistinctProteinCounts(complexInst);
			int complexTotalProteinCounts = complexProteinCounts.get(0);
			int complexInferrableProteinCounts = complexProteinCounts.get(1);
//			int complexMax = complexProteinCounts.get(2); // Doesn't get used, since MaxHomologue isn't a valid attribute anymore.
			
			// Filtering based on results of ProteinCounts and threshold (currently hard-coded at 75%).
			int percent = 0;
			if (complexTotalProteinCounts > 0)
			{
				percent = (complexInferrableProteinCounts * 100)/complexTotalProteinCounts;
			}
			if (!override)
			{
				if ((complexTotalProteinCounts > 0 && complexInferrableProteinCounts == 0) || percent < 75)
				{
					return nullInst;
				}
			}
			
			GKInstance infComplexInst = InstanceUtilities.createNewInferredGKInstance(complexInst);
			infComplexInst.addAttributeValue(summation, complexSummationInst);
			infComplexInst.addAttributeValue(name, complexInst.getAttributeValue(name));
			ArrayList<GKInstance> infComponentInstances = new ArrayList<GKInstance>();
			// Inference handling is different depending on if it is a Complex or a Polymer. Complexes will infer all 'components' while Polymers will infer all 'repeatedUnits'.
			if (complexInst.getSchemClass().isa(Complex))
			{
				for (Object componentInst : complexInst.getAttributeValuesList(hasComponent))
				{	
					infComponentInstances.add(createOrthoEntity((GKInstance) componentInst, true));
				}
				infComplexInst.addAttributeValue(hasComponent, infComponentInstances);
			} else  if (complexInst.getSchemClass().isa(Polymer))
			{
				for (Object repeatedUnitInst : complexInst.getAttributeValuesList(repeatedUnit))
				{		
					infComponentInstances.add(createOrthoEntity((GKInstance) repeatedUnitInst, true));
				}
				infComplexInst.addAttributeValue(repeatedUnit, infComponentInstances);
			} else {
				System.out.println(complexInst + " is not a Complex or a Polymer");
				return nullInst;
			}
			infComplexInst.setAttributeValue(_displayName, complexInst.getAttributeValue(_displayName));
			
			// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which is slow.
			String cacheKey = InstanceUtilities.getCacheKey((GKSchemaClass) infComplexInst.getSchemClass(), infComplexInst);
			if (complexIdenticals.get(cacheKey) != null)
			{
				infComplexInst = complexIdenticals.get(cacheKey);
			} else {
				infComplexInst = InstanceUtilities.checkForIdenticalInstances(infComplexInst);
				complexIdenticals.put(cacheKey, infComplexInst);
			}

			infComplexInst = InstanceUtilities.addAttributeValueIfNecessary(infComplexInst, complexInst, inferredFrom);
			dba.updateInstanceAttribute(infComplexInst, inferredFrom);
			complexInst = InstanceUtilities.addAttributeValueIfNecessary(complexInst, infComplexInst, inferredTo);
			dba.updateInstanceAttribute(complexInst, inferredTo);
			
			if (override)
			{
				return infComplexInst;
			} 
			complexPolymerIdenticals.put(complexInst, infComplexInst);
		}
		return complexPolymerIdenticals.get(complexInst);
	}
	
	// EntitySet inference function. This function will initially call createOrthoEntity on all 'members' before filtering by the type of EntitySet (Open, Candidate, or Defined Sets) and completing a specific inference.
	// Important to note is that while there are multiple cases where createOrthoEntity is called (for members and candidates) in createInfEntitySet, the override functionality is not used here. 
	// Presumably, this is because the instances aren't a constituent part of a single instance (as in Complexes), but rather are stand-alone ones that also happen to be included in a Set. 
	// This means they should be subject  to the stringency of a typical instance, rather then using override to create mock instances that allow an instance to be inferred more easily.
	private static GKInstance createInfEntitySet(GKInstance entitySetInst, boolean override) throws InvalidAttributeException, Exception
	{
		if (inferredEntitySetIdenticals.get(entitySetInst) == null)
		{
			// Equivalent to infer_members function in infer_events.pl
			HashSet<String> existingMemberInstances = new HashSet<String>();
			ArrayList<GKInstance> membersList = new ArrayList<GKInstance>();
			for (Object memberInst : entitySetInst.getAttributeValuesList(hasMember))
			{
				GKInstance infMemberInst = createOrthoEntity((GKInstance) memberInst, false);
				if (infMemberInst != null && !existingMemberInstances.contains(infMemberInst.getAttributeValue(name).toString()))
				{
					existingMemberInstances.add(infMemberInst.getAttributeValue(name).toString());
					membersList.add(infMemberInst);
				}
			}
			// Begin inference of EntitySet
			GKInstance infEntitySetInst = InstanceUtilities.createNewInferredGKInstance(entitySetInst);
			infEntitySetInst.addAttributeValue(name, entitySetInst.getAttributeValuesList(name));
			infEntitySetInst.addAttributeValue(hasMember, membersList);
			// Begin specific inference process for each type of DefinedSet entity.
			List<Integer> entitySetProteinCounts = ProteinCountUtility.getDistinctProteinCounts(entitySetInst);
			int entitySetTotalCount = entitySetProteinCounts.get(0);
			int entitySetInferrableCount = entitySetProteinCounts.get(1);
//				int entitySetMax = entitySetProteinCounts.get(2);  // Doesn't get used, since MaxHomologue isn't a valid attribute anymore
			
			// Filtering based on ProteinCount results
			if (!override && entitySetTotalCount > 0 && entitySetInferrableCount == 0)
			{
				return nullInst;
			}
			
			if (entitySetInst.getSchemClass().isa(CandidateSet))
			{
				HashSet<String> existingCandidateInstances = new HashSet<String>();
				ArrayList<GKInstance> candidatesList = new ArrayList<GKInstance>();
				// Equivalent to infer_members function in infer_events.pl
				for (Object candidateInst : entitySetInst.getAttributeValuesList(hasCandidate))
				{
					GKInstance infCandidateInst = createOrthoEntity((GKInstance) candidateInst, false);
					if (infCandidateInst != null && !existingMemberInstances.contains(infCandidateInst.getAttributeValue(name).toString()) && !existingCandidateInstances.contains(infCandidateInst.getAttributeValue(name).toString()))
					{
						existingCandidateInstances.add(infCandidateInst.getAttributeValue(name).toString());
						candidatesList.add(infCandidateInst);
					}
				}
				// Handling of CandidateSets
				if (candidatesList.size() > 0)
				{
					infEntitySetInst.addAttributeValue(hasCandidate, candidatesList);
				} else {
					if (membersList.size() != 0)
					{
						if (membersList.size() == 1)
						{
							infEntitySetInst = membersList.get(0);
						} else {
							SchemaClass definedSetClass = dba.getSchema().getClassByName(DefinedSet);
							GKInstance infDefinedSetInst = new GKInstance(definedSetClass);
							infDefinedSetInst.setDbAdaptor(dba);
							infDefinedSetInst.addAttributeValue(created, instanceEditInst);
							infDefinedSetInst.setAttributeValue(name, infEntitySetInst.getAttributeValuesList(name));
							infDefinedSetInst.setAttributeValue(hasMember, membersList);
							if (entitySetInst.getSchemClass().isValidAttribute(compartment) && entitySetInst.getAttributeValue(compartment) != null) 
							{
								for (Object compartmentInst : entitySetInst.getAttributeValuesList(compartment)) {
									GKInstance compartmentInstGk = (GKInstance) compartmentInst;
									if (compartmentInstGk.getSchemClass().isa(Compartment)) 
									{
										infDefinedSetInst.addAttributeValue(compartment, compartmentInstGk);
									} else {
										GKInstance newCompartmentInst = InstanceUtilities.createCompartmentInstance(compartmentInstGk);
										infDefinedSetInst.addAttributeValue(compartment, newCompartmentInst);
									}
								}
							}
							infDefinedSetInst.addAttributeValue(species, speciesInst);
							infEntitySetInst = infDefinedSetInst;
						}
					} else {
						if (override)
						{
							infEntitySetInst = InstanceUtilities.createMockGKInstance(entitySetInst);
						} else {
							return nullInst;
						}
					}
				}	
			} else if (entitySetInst.getSchemClass().isa(DefinedSet))
			{
				if (membersList.size() == 0)
				{
					if (override)
					{
						return InstanceUtilities.createMockGKInstance(entitySetInst);
					} else {
						return nullInst;
					}
				} else if (membersList.size() == 1) 
				{
					infEntitySetInst = membersList.get(0);
				}
				// If it has more than 1 member (which is the logic that would theoretically go here), nothing happens; 
				// All members are stored in this inferred instances 'hasMember' attribute near the beginning of this function.
			}

			infEntitySetInst.setAttributeValue(_displayName, entitySetInst.getAttributeValue(_displayName));
			// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which is slow.
			String cacheKey = InstanceUtilities.getCacheKey((GKSchemaClass) infEntitySetInst.getSchemClass(), infEntitySetInst);
			if (entitySetIdenticals.get(cacheKey) != null)
			{
				infEntitySetInst = entitySetIdenticals.get(cacheKey);
			} else {
				infEntitySetInst = InstanceUtilities.checkForIdenticalInstances(infEntitySetInst);
				entitySetIdenticals.put(cacheKey, infEntitySetInst);
			}
			if (infEntitySetInst.getSchemClass().isValidAttribute(species) && entitySetInst.getAttributeValue(species) != null)
			{
				infEntitySetInst = InstanceUtilities.addAttributeValueIfNecessary(infEntitySetInst, entitySetInst, inferredFrom);
				dba.updateInstanceAttribute(infEntitySetInst, inferredFrom);
				entitySetInst = InstanceUtilities.addAttributeValueIfNecessary(entitySetInst, infEntitySetInst, inferredTo);
				dba.updateInstanceAttribute(entitySetInst, inferredTo);
			}
			if (override)
			{
			return infEntitySetInst;
			}
			inferredEntitySetIdenticals.put(entitySetInst, infEntitySetInst);
		}
		return inferredEntitySetIdenticals.get(entitySetInst);
	}
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}
	
	public static void setSpeciesInstance(GKInstance speciesInstCopy)
	{
		speciesInst = speciesInstCopy;
	}
	
	public static void setInstanceEdit(GKInstance instanceEditCopy) 
	{
		instanceEditInst = instanceEditCopy;
	}
	
	public static void setComplexSummationInstance() throws Exception
	{
		complexSummationInst = new GKInstance(dba.getSchema().getClassByName(Summation));
		complexSummationInst.setDbAdaptor(dba);
		complexSummationInst.addAttributeValue(created, instanceEditInst);
		String complexSummationText = "This complex/polymer has been computationally inferred (based on Ensembl Compara) from a complex/polymer involved in an event that has been demonstrated in another species.";
		complexSummationInst.addAttributeValue(text, complexSummationText);
		complexSummationInst.setAttributeValue(_displayName, complexSummationText);
		complexSummationInst = InstanceUtilities.checkForIdenticalInstances(complexSummationInst);
	}
	
	public static void resetVariables()
	{
		orthologousEntityIdenticals = new HashMap<GKInstance,GKInstance>();
		homolEWASIdenticals = new HashMap<GKInstance,GKInstance>();
		complexPolymerIdenticals = new HashMap<GKInstance, GKInstance>();
		inferredEntitySetIdenticals = new HashMap<GKInstance, GKInstance>();
		definedSetIdenticals = new HashMap<String,GKInstance>();
		complexIdenticals = new HashMap<String,GKInstance>();
		entitySetIdenticals = new HashMap<String,GKInstance>();
	}
}
