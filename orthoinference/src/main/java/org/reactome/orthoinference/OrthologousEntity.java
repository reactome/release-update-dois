package org.reactome.orthoinference;

import java.util.ArrayList;
import java.util.HashMap;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;

public class OrthologousEntity {
	
	private static HashMap<GKInstance, GKInstance> orthologousEntity = new HashMap<GKInstance,GKInstance>();
	private static MySQLAdaptor dba;
	static GenerateInstance createInferredInstance = new GenerateInstance();
	
	public void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		OrthologousEntity.dba = dbAdaptor;
	}
	
	private static GKInstance speciesInst = null;
	
	// Sets the species instance for inferEWAS to use
	public void setSpeciesInst(GKInstance speciesInstCopy)
	{
		speciesInst = speciesInstCopy;
	}

	public static GKInstance createOrthoEntity(GKInstance attributeInst, boolean override) throws InvalidAttributeException, Exception
	{
		GKInstance infEntity = null;
		if (attributeInst.getSchemClass().isValidAttribute("species"))
		{
			if (orthologousEntity.get(attributeInst) == null)
			{
		// TODO: has_species function 
			if (attributeInst.getSchemClass().isa("GenomeEncodedEntity"))
			{
				if (attributeInst.getSchemClass().toString().contains("GenomeEncodedEntity"))
				{
					//TODO: create ghost
				} else {
					System.out.println("GEE");
					infEntity = OrthologousEntity.createInfGEE(attributeInst, override);
				}
			} else if (attributeInst.getSchemClass().isa("Complex") || attributeInst.getSchemClass().isa("Polymer"))
			{
				System.out.println("Complex/Polymer");
				infEntity = OrthologousEntity.createInfComplexPolymer(attributeInst, override);
			} else if (attributeInst.getSchemClass().isa("EntitySet"))
			{
				//TODO: Check species attribute
//				OrthologousEntity.createInfEntitySet(attributeInst);
			} else if (attributeInst.getSchemClass().isa("SimpleEntity"))
			{
//				System.out.println("SimpleEntity");
			} else {
//				System.out.println("Unknown");
			}
			//TODO: %orthologous_entity
			orthologousEntity.put(attributeInst, infEntity);
		} 
			//TODO: Properly 'unless' evaluation
			GKInstance existingInst = orthologousEntity.get(attributeInst);
			return existingInst;
		} else {
			//TODO: check intracellular; if flag create clone;
			System.out.println("Invalid");
			return attributeInst;
		}
	}
	
	public static GKInstance createInfGEE(GKInstance geeInst, boolean override) throws InvalidAttributeException, Exception
	{
		//TODO: %homol_gee; create_ghost;
		InferEWAS ewasInferrer = new InferEWAS();
		ArrayList<GKInstance> infEWASInstances = ewasInferrer.inferEWAS(geeInst);
		SchemaClass definedSetClass = dba.getSchema().getClassByName(ReactomeJavaConstants.DefinedSet);
		GKInstance definedSetInst = new GKInstance(definedSetClass);
		if (infEWASInstances.size() > 1)
		{
			try {
			// TODO: Instance Edit; Check Intracellular; $opt_filt (??); check for identical instances; add attribute values if necessary - inferredFrom/To; %homol_gee
			String definedSetName = "Homologues of " + geeInst.getAttributeValue("name");
			definedSetInst.addAttributeValue(ReactomeJavaConstants.name, definedSetName);
			definedSetInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
			definedSetInst.addAttributeValue(ReactomeJavaConstants.hasMember, infEWASInstances);
			} catch (Exception e) {
				e.printStackTrace();
			}	
		} else if (infEWASInstances.size() == 1)
		{
			//TODO: %homol_gee
			definedSetInst = infEWASInstances.get(0);
		} else {
			//TODO: create_ghost if override; Handle empty return
			if (override) {
			GKInstance mockedInst = GenerateInstance.newMockGKInstance(geeInst);
			return mockedInst;
			} else {
				GKInstance nullInst = null;
				return nullInst;
			}
		}
		return definedSetInst;
	}
	
	public static GKInstance createInfComplexPolymer(GKInstance complexInst, boolean override)
	{
		//TODO: %inferred_cp; count distinct proteins; filter based on returned protein count and threshold
		GKInstance infComplexInst = createInferredInstance.newInferredGKInstance(complexInst);
		GKInstance complexSummation = new GKInstance(dba.getSchema().getClassByName(ReactomeJavaConstants.Summation));
		try {
			//TODO: Remove brackets from name
			infComplexInst.addAttributeValue(ReactomeJavaConstants.name, complexInst.getAttributeValue("name"));
			complexSummation.addAttributeValue(ReactomeJavaConstants.text, "This complex/polymer has been computationally inferred (based on Ensembl Compara) from a complex/polymer involved in an event that has been demonstrated in another species.");
			infComplexInst.addAttributeValue(ReactomeJavaConstants.summation, complexSummation);
			//TODO: check for identical instances (complexSummation)
			
			ArrayList<GKInstance> infComponents = new ArrayList<GKInstance>();
			if (complexInst.getSchemClass().isa(ReactomeJavaConstants.Complex))
			{
				for (Object componentInst : complexInst.getAttributeValuesList(ReactomeJavaConstants.hasComponent))
				{		
					infComponents.add(OrthologousEntity.createOrthoEntity((GKInstance) componentInst, true));
				}
			infComplexInst.addAttributeValue(ReactomeJavaConstants.hasComponent, infComponents);
			} else {
				for (Object componentInst : complexInst.getAttributeValuesList(ReactomeJavaConstants.repeatedUnit))
				{		
					infComponents.add(OrthologousEntity.createOrthoEntity((GKInstance) componentInst, true));
				}
			infComplexInst.addAttributeValue(ReactomeJavaConstants.repeatedUnit, infComponents);
			}
			
			//TODO: Add total,inferred,max proteins count; inferredTo & inferredFrom; update; add to hash
		} catch (Exception e) {
			e.printStackTrace();
		}
		return infComplexInst;
	}
	
	
	
	
	public static void createInfEntitySet(GKInstance attributeInst)
	{
		System.out.println("Hello");
	}
}
