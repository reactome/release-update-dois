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
	
	private static HashMap<GKInstance, GKInstance> orthologousEntity = new HashMap<GKInstance,GKInstance>();
	private static HashMap<GKInstance, GKInstance> homolGEE = new HashMap<GKInstance,GKInstance>();
	private static HashMap<GKInstance, GKInstance> complexPolymer = new HashMap<GKInstance, GKInstance>();
	private static HashMap<GKInstance, GKInstance> inferredGSE = new HashMap<GKInstance, GKInstance>();
	private static MySQLAdaptor dba;
	static GenerateInstance createInferredInstance = new GenerateInstance();
	static GKInstance nullInst = null;
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		OrthologousEntity.dba = dbAdaptor;
	}
	
	private static GKInstance speciesInst = null;
	
	// Sets the species instance for inferEWAS to use
	public static void setSpeciesInst(GKInstance speciesInstCopy)
	{
		speciesInst = speciesInstCopy;
	}

	public static GKInstance createOrthoEntity(GKInstance entityInst, boolean override) throws InvalidAttributeException, Exception
	{
		GKInstance infEntity = null;
		if (entityInst.getSchemClass().isValidAttribute("species"))
		{
			// TODO: Make sure this null check actually works
			if (orthologousEntity.get(entityInst) == null)
			{
				// TODO: Verify this works for all instance types; Move to its own file
				if (!SpeciesCheck.hasSpecies(entityInst))
				{
					infEntity = entityInst;
				} else if (entityInst.getSchemClass().isa("GenomeEncodedEntity"))
				{
					// Change to 'EWAS'? So that reading this makes more sense
					if (entityInst.getSchemClass().toString().contains("GenomeEncodedEntity"))
					{
						if (override)
						{
							GKInstance mockedInst = GenerateInstance.newMockGKInstance(entityInst);
							return mockedInst;
						}
					} else {
						infEntity = OrthologousEntity.createInfGEE(entityInst, override);
					}
				} else if (entityInst.getSchemClass().isa("Complex") || entityInst.getSchemClass().isa("Polymer"))
				{
					infEntity = OrthologousEntity.createInfComplexPolymer(entityInst, override);
				} else if (entityInst.getSchemClass().isa("EntitySet"))
				{
					if (entityInst.getAttributeValue("species") != null)
					{
						infEntity = OrthologousEntity.createInfEntitySet(entityInst, override);
					} else {
						infEntity = entityInst;
					}
				} else if (entityInst.getSchemClass().isa("SimpleEntity"))
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
	

	// TODO: Naming change; Function description
	public static GKInstance createInfGEE(GKInstance geeInst, boolean override) throws InvalidAttributeException, Exception
	{
		if (homolGEE.get(geeInst) == null)
		{
			ArrayList<GKInstance> infEWASInstances = InferEWAS.inferEWAS(geeInst);
			if (infEWASInstances.size() > 1)
			{
				// TODO: Instance Edit; Check Intracellular; add attribute values if necessary - inferredFrom/To
				SchemaClass definedSetClass = dba.getSchema().getClassByName(ReactomeJavaConstants.DefinedSet);
				GKInstance definedSetInst = new GKInstance(definedSetClass);
				definedSetInst.setDbAdaptor(dba);
				String definedSetName = "Homologues of " + geeInst.getAttributeValue("name");
				definedSetInst.addAttributeValue(ReactomeJavaConstants.name, definedSetName);
				definedSetInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
				definedSetInst.addAttributeValue(ReactomeJavaConstants.hasMember, infEWASInstances);
				definedSetInst = GenerateInstance.checkForIdenticalInstances(definedSetInst);
				homolGEE.put(geeInst, definedSetInst);
			} else if (infEWASInstances.size() == 1)
			{
				homolGEE.put(geeInst, infEWASInstances.get(0));
			} else {
				if (override) {
				GKInstance mockedInst = GenerateInstance.newMockGKInstance(geeInst);
				return mockedInst;
				} else {
					return nullInst;
				}
			}
		}
		return homolGEE.get(geeInst);
	}
	
	public static GKInstance createInfComplexPolymer(GKInstance complexInst, boolean override) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		if (complexPolymer.get(complexInst) == null)
		{
			//TODO: filter based on returned protein count and threshold
			GKInstance infComplexInst = GenerateInstance.newInferredGKInstance(complexInst);
			
			List<Integer> complexProteinCounts = ProteinCount.countDistinctProteins(complexInst);
			int complexTotal = complexProteinCounts.get(0);
			int complexInferred = complexProteinCounts.get(1);
//			int complexMax = complexProteinCounts.get(2); // Doesn't get used, since MaxHomologue isn't a valid attribute
			
			int percent = 0;
			if (complexTotal > 0)
			{
				percent = (complexInferred * 100)/complexTotal;
			}
			if (!override)
			{
				if (complexTotal > 0 && complexInferred == 0)
				{
					return nullInst;
				}
				if (percent < 75)
				{
					return nullInst;
				}
			}

			GKInstance complexSummation = new GKInstance(dba.getSchema().getClassByName(ReactomeJavaConstants.Summation));
			complexSummation.addAttributeValue(ReactomeJavaConstants.text, "This complex/polymer has been computationally inferred (based on Ensembl Compara) from a complex/polymer involved in an event that has been demonstrated in another species.");
			complexSummation = GenerateInstance.checkForIdenticalInstances(complexSummation);
			infComplexInst.addAttributeValue(ReactomeJavaConstants.summation, complexSummation);
			//TODO: Remove brackets from name
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
				// TODO: Verify Polymer things are working
				for (Object repeatedUnitInst : complexInst.getAttributeValuesList(ReactomeJavaConstants.repeatedUnit))
				{		
					infComponents.add(OrthologousEntity.createOrthoEntity((GKInstance) repeatedUnitInst, true));
				}
			infComplexInst.addAttributeValue(ReactomeJavaConstants.repeatedUnit, infComponents);
			}
			infComplexInst = GenerateInstance.checkForIdenticalInstances(infComplexInst);
			//TODO: inferredTo & inferredFrom; update;
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
		//TODO: Filter based on the HashSet
		if (inferredGSE.get(entitySetInst) == null)
		{
			// Equivalent to infer_members
			HashSet<String> existingMembers = new HashSet<String>();
			ArrayList<GKInstance> membersList = new ArrayList<GKInstance>();
			for (Object memberInst : entitySetInst.getAttributeValuesList(ReactomeJavaConstants.hasMember))
			{
				GKInstance infMember = OrthologousEntity.createOrthoEntity((GKInstance) memberInst, false);
				if (infMember != null)
				{
					existingMembers.add(infMember.getAttributeValue("name").toString());
					membersList.add(infMember);
				}
			}
			GKInstance infEntitySetInst = GenerateInstance.newInferredGKInstance(entitySetInst);
			infEntitySetInst.addAttributeValue(ReactomeJavaConstants.name, entitySetInst.getAttributeValue("name"));
			infEntitySetInst.addAttributeValue(ReactomeJavaConstants.hasMember, membersList);
			if (entitySetInst.getSchemClass().isa(ReactomeJavaConstants.OpenSet))
			{
				infEntitySetInst.addAttributeValue(ReactomeJavaConstants.referenceEntity, entitySetInst.getAttributeValue("referenceEntity"));
			} else {
				List<Integer> entitySetProteinCounts = ProteinCount.countDistinctProteins(entitySetInst);
				int entitySetTotal = entitySetProteinCounts.get(0);
				int entitySetInferred = entitySetProteinCounts.get(1);
//				int entitySetMax = entitySetProteinCounts.get(2);  // Doesn't get used, since MaxHomologue isn't a valid attribute
				if (!override && entitySetTotal > 0 && entitySetInferred == 0)
				{
					return nullInst;
				}
				
				if (entitySetInst.getSchemClass().isa(ReactomeJavaConstants.CandidateSet))
				{
					// TODO: Filter based on the HashSet
					HashSet<String> existingCandidates = new HashSet<String>();
					ArrayList<GKInstance> candidatesListUnfiltered = new ArrayList<GKInstance>();
					// Equivalent to infer_members
					for (Object candidateInst : entitySetInst.getAttributeValuesList(ReactomeJavaConstants.hasCandidate))
					{
						GKInstance infCandidate = OrthologousEntity.createOrthoEntity((GKInstance) candidateInst, false);
						if (infCandidate != null)
						{
							existingCandidates.add(infCandidate.getAttributeValue("name").toString());
							candidatesListUnfiltered.add(infCandidate);
						}
					}
					// Check for duplicate instances between membersList and candidatesList, keeping only unique ones	
					ArrayList<GKInstance> candidatesList = new ArrayList<GKInstance>();
					for (GKInstance candidate : candidatesListUnfiltered)
					{
						int memberCount = 0;
						for (GKInstance member : membersList)
						{
							if (candidate == member)
							{
								memberCount++;
							}
						}
						if (memberCount == 0)
						{
							candidatesList.add(candidate);
						}
					}
					// Handling of CandidateSets
					if (candidatesList.size() == 1)
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
				// add attribute value if necessary InferredFrom/To; update;
			}
			if (override)
			{
			return infEntitySetInst;
			}
			inferredGSE.put(entitySetInst, infEntitySetInst);
		}
		return inferredGSE.get(entitySetInst);
	}
}
