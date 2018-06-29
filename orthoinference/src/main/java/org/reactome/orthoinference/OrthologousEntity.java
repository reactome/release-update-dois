package org.reactome.orthoinference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;

public class OrthologousEntity {
	
	private static MySQLAdaptor dba;
	private static HashMap<GKInstance, GKInstance> orthologousEntity = new HashMap<GKInstance,GKInstance>();
	private static HashMap<GKInstance, GKInstance> homolGEE = new HashMap<GKInstance,GKInstance>();
	private static HashMap<GKInstance, GKInstance> complexPolymer = new HashMap<GKInstance, GKInstance>();
	private static HashMap<GKInstance, GKInstance> inferredGSE = new HashMap<GKInstance, GKInstance>();
	private static GKInstance complexSummationInst;
	private static GKInstance speciesInst;
	static GKInstance nullInst = null;

	public static GKInstance createOrthoEntity(GKInstance entityInst, boolean override) throws InvalidAttributeException, Exception
	{
		GKInstance infEntity = null;
		if (entityInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.species))
		{
			// TODO: Check efficacy of null check; hasSpecies function on all instance types
			if (orthologousEntity.get(entityInst) == null)
			{
				if (!SpeciesCheck.hasSpecies(entityInst))
				{
					infEntity = entityInst;
				} else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.GenomeEncodedEntity))
				{
					if (entityInst.getSchemClass().toString().contains(ReactomeJavaConstants.EntityWithAccessionedSequence))
					{
						infEntity = OrthologousEntity.createInfEWAS(entityInst, override);
					} else {
						if (override)
						{
							GKInstance mockedInst = GenerateInstance.newMockGKInstance(entityInst);
							return mockedInst;
						}
					}
				} else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.Complex) || entityInst.getSchemClass().isa(ReactomeJavaConstants.Polymer))
				{
					infEntity = OrthologousEntity.createInfComplexPolymer(entityInst, override);
				} else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.EntitySet))
				{
					if (entityInst.getAttributeValue(ReactomeJavaConstants.species) != null)
					{
						infEntity = OrthologousEntity.createInfEntitySet(entityInst, override);
					} else {
						infEntity = entityInst;
					}
				} else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity))
				{
					infEntity = entityInst;
				} else {
					//TODO: Unknown Class
				}
				//TODO: %orthologous_entity
				if (override)
				{
					return infEntity;
				}
				
				orthologousEntity.put(entityInst, infEntity);
			} 
			return orthologousEntity.get(entityInst);
		} else {
			//TODO: check intracellular; if flag create clone;
			return entityInst;
		}
	}
	
	public static GKInstance createInfEWAS(GKInstance ewasInst, boolean override) throws InvalidAttributeException, Exception
	{
		if (homolGEE.get(ewasInst) == null)
		{
			ArrayList<GKInstance> infEWASInstances = InferEWAS.inferEWAS(ewasInst);
			if (infEWASInstances.size() > 1)
			{
				// TODO: Instance Edit; Check Intracellular; opt_filt logic
				SchemaClass definedSetClass = dba.getSchema().getClassByName(ReactomeJavaConstants.DefinedSet);
				GKInstance definedSetInst = new GKInstance(definedSetClass);
				definedSetInst.setDbAdaptor(dba);
				String definedSetName = "Homologues of " + ewasInst.getAttributeValue("name");
				definedSetInst.addAttributeValue(ReactomeJavaConstants.name, definedSetName);
				definedSetInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
				definedSetInst.addAttributeValue(ReactomeJavaConstants.hasMember, infEWASInstances);
				definedSetInst = GenerateInstance.checkForIdenticalInstances(definedSetInst);
				if (GenerateInstance.addAttributeValueIfNeccesary(definedSetInst, ewasInst, ReactomeJavaConstants.inferredFrom))
				{
					definedSetInst.addAttributeValue(ReactomeJavaConstants.inferredFrom, ewasInst);
				}
				dba.updateInstanceAttribute(definedSetInst, ReactomeJavaConstants.inferredFrom);
				if (GenerateInstance.addAttributeValueIfNeccesary(ewasInst, definedSetInst, ReactomeJavaConstants.inferredTo))
				{
					ewasInst.addAttributeValue(ReactomeJavaConstants.inferredTo, definedSetInst);
				}
				dba.updateInstanceAttribute(ewasInst, ReactomeJavaConstants.inferredTo);
				homolGEE.put(ewasInst, definedSetInst);
			} else if (infEWASInstances.size() == 1)
			{
				homolGEE.put(ewasInst, infEWASInstances.get(0));
			} else {
				if (override) {
				GKInstance mockedInst = GenerateInstance.newMockGKInstance(ewasInst);
				return mockedInst;
				} else {
					return nullInst;
				}
			}
		}
		return homolGEE.get(ewasInst);
	}
	
	public static GKInstance createInfComplexPolymer(GKInstance complexInst, boolean override) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		if (complexPolymer.get(complexInst) == null)
		{
			List<Integer> complexProteinCounts = ProteinCount.countDistinctProteins(complexInst);
			int complexTotal = complexProteinCounts.get(0);
			int complexInferrable = complexProteinCounts.get(1);
//			int complexMax = complexProteinCounts.get(2); // Doesn't get used, since MaxHomologue isn't a valid attribute anymore
			int percent = 0;
			if (complexTotal > 0)
			{
				percent = (complexInferrable * 100)/complexTotal;
			}
			if (!override)
			{
				if (complexTotal > 0 && complexInferrable == 0)
				{
					return nullInst;
				}
				if (percent < 75)
				{
					return nullInst;
				}
			}
			
			GKInstance infComplexInst = GenerateInstance.newInferredGKInstance(complexInst);
			infComplexInst.addAttributeValue(ReactomeJavaConstants.summation, complexSummationInst);
			//TODO: Remove brackets from name?
			infComplexInst.addAttributeValue(ReactomeJavaConstants.name, complexInst.getAttributeValue(ReactomeJavaConstants.name));
			ArrayList<GKInstance> infComponents = new ArrayList<GKInstance>();
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
			infComplexInst = GenerateInstance.checkForIdenticalInstances(infComplexInst);
			
			if (infComplexInst.getDBID() != complexInst.getDBID())
			{
				if (GenerateInstance.addAttributeValueIfNeccesary(infComplexInst, complexInst, ReactomeJavaConstants.inferredFrom))
				{
					infComplexInst.addAttributeValue(ReactomeJavaConstants.inferredFrom, complexInst);
				}
				dba.updateInstanceAttribute(infComplexInst, ReactomeJavaConstants.inferredFrom);
				if (GenerateInstance.addAttributeValueIfNeccesary(complexInst, infComplexInst, ReactomeJavaConstants.inferredTo))
				{
					complexInst.addAttributeValue(ReactomeJavaConstants.inferredTo, infComplexInst);
				}
				dba.updateInstanceAttribute(complexInst, ReactomeJavaConstants.inferredTo);
			}
			
			if (override)
			{
				return infComplexInst;
			} else {
				complexPolymer.put(complexInst, infComplexInst);
			}
		}
		return complexPolymer.get(complexInst);
	}
	//TODO: The organization of this function could probably be re-organized
	public static GKInstance createInfEntitySet(GKInstance entitySetInst, boolean override) throws InvalidAttributeException, Exception
	{
		if (inferredGSE.get(entitySetInst) == null)
		{
			// Equivalent to infer_members
			HashSet<String> existingMembers = new HashSet<String>();
			ArrayList<GKInstance> membersList = new ArrayList<GKInstance>();
			for (Object memberInst : entitySetInst.getAttributeValuesList(ReactomeJavaConstants.hasMember))
			{
				GKInstance infMember = OrthologousEntity.createOrthoEntity((GKInstance) memberInst, false);
				if (infMember != null && !existingMembers.contains(infMember.getAttributeValue("name").toString()))
				{
					existingMembers.add(infMember.getAttributeValue("name").toString());
					membersList.add(infMember);
				}
			}
			GKInstance infEntitySetInst = GenerateInstance.newInferredGKInstance(entitySetInst);
			infEntitySetInst.addAttributeValue(ReactomeJavaConstants.name, entitySetInst.getAttributeValuesList("name"));
			infEntitySetInst.addAttributeValue(ReactomeJavaConstants.hasMember, membersList);
			if (entitySetInst.getSchemClass().isa(ReactomeJavaConstants.OpenSet))
			{
				infEntitySetInst.addAttributeValue(ReactomeJavaConstants.referenceEntity, entitySetInst.getAttributeValuesList("referenceEntity"));
			} else {
				List<Integer> entitySetProteinCounts = ProteinCount.countDistinctProteins(entitySetInst);
				int entitySetTotal = entitySetProteinCounts.get(0);
				int entitySetInferrable = entitySetProteinCounts.get(1);
//				int entitySetMax = entitySetProteinCounts.get(2);  // Doesn't get used, since MaxHomologue isn't a valid attribute anymore
				if (!override && entitySetTotal > 0 && entitySetInferrable == 0)
				{
					return nullInst;
				}
				
				if (entitySetInst.getSchemClass().isa(ReactomeJavaConstants.CandidateSet))
				{
					HashSet<String> existingCandidates = new HashSet<String>();
					ArrayList<GKInstance> candidatesList = new ArrayList<GKInstance>();
					// Equivalent to infer_members
					for (Object candidateInst : entitySetInst.getAttributeValuesList(ReactomeJavaConstants.hasCandidate))
					{
						GKInstance infCandidate = OrthologousEntity.createOrthoEntity((GKInstance) candidateInst, false);
						if (infCandidate != null && !existingMembers.contains(infCandidate.getAttributeValue("name").toString()) && !existingCandidates.contains(infCandidate.getAttributeValue("name").toString()))
						{
							existingCandidates.add(infCandidate.getAttributeValue("name").toString());
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
								SchemaClass definedSetClass = dba.getSchema().getClassByName(ReactomeJavaConstants.DefinedSet);
								GKInstance definedSetInst = new GKInstance(definedSetClass);
								definedSetInst.setDbAdaptor(dba);
								definedSetInst.setAttributeValue(ReactomeJavaConstants.name, infEntitySetInst.getAttributeValuesList(ReactomeJavaConstants.name));
								definedSetInst.setAttributeValue(ReactomeJavaConstants.hasMember, membersList);
								infEntitySetInst = definedSetInst;
							}
						} else {
							if (override)
							{
								infEntitySetInst = GenerateInstance.newMockGKInstance(entitySetInst);
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
							return GenerateInstance.newMockGKInstance(entitySetInst);
						} else {
							return nullInst;
						}
					} else if (membersList.size() == 1) {
						infEntitySetInst = membersList.get(0);
					}
					// If it has more than 1 member, nothing happens here; all members are stored in this inferred instances 'HasMember' attribute
				}
			}
			infEntitySetInst = GenerateInstance.checkForIdenticalInstances(infEntitySetInst);
			if (infEntitySetInst.getSchemClass().isValidAttribute("species") && entitySetInst.getAttributeValue("species") != null)
			{
				if (GenerateInstance.addAttributeValueIfNeccesary(infEntitySetInst, entitySetInst, ReactomeJavaConstants.inferredFrom))
				{
					infEntitySetInst.addAttributeValue(ReactomeJavaConstants.inferredFrom, entitySetInst);
				}
				dba.updateInstanceAttribute(infEntitySetInst, ReactomeJavaConstants.inferredFrom);
				if (GenerateInstance.addAttributeValueIfNeccesary(entitySetInst, infEntitySetInst, ReactomeJavaConstants.inferredTo))
				{
					entitySetInst.addAttributeValue(ReactomeJavaConstants.inferredTo, infEntitySetInst);
				}
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
	public static void setSpeciesInst(GKInstance speciesInstCopy)
	{
		speciesInst = speciesInstCopy;
	}
	public static void setComplexSummationInst() throws Exception
	{
		complexSummationInst = new GKInstance(dba.getSchema().getClassByName(ReactomeJavaConstants.Summation));
		complexSummationInst.addAttributeValue(ReactomeJavaConstants.text, "This complex/polymer has been computationally inferred (based on Ensembl Compara) from a complex/polymer involved in an event that has been demonstrated in another species.");
		complexSummationInst = GenerateInstance.checkForIdenticalInstances(complexSummationInst);
	}
}
