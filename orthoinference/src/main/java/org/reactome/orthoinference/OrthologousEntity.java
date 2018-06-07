package org.reactome.orthoinference;

import java.util.ArrayList;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

public class OrthologousEntity {
	
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

	public GKInstance createOrthoEntity(GKInstance attributeInst)
	{
		GKInstance infEntity = null;
		if (attributeInst.getSchemClass().isValidAttribute("species"))
		{
		// TODO: has_species function 
			
			if (attributeInst.getSchemClass().isa("GenomeEncodedEntity"))
			{
				System.out.println("GEE");
				if (!attributeInst.getSchemClass().toString().contains("GenomeEncodedEntity"))
				{
					infEntity = OrthologousEntity.createInfGEE(attributeInst);
				}
			} else if (attributeInst.getSchemClass().isa("Complex") || attributeInst.getSchemClass().isa("Polymer"))
			{
//				System.out.println("Complex");
				OrthologousEntity.createInfComplexPolymer(attributeInst);
				
			} else if (attributeInst.getSchemClass().isa("EntitySet"))
			{
				//TODO: Check species attribute
//				System.out.println("EntitySet");
//				OrthologousEntity.createInfEntitySet(attributeInst);
			} else if (attributeInst.getSchemClass().isa("SimpleEntity"))
			{
//				System.out.println("SimpleEntity");
			} else {
//				System.out.println("Unknown");
			}
		}
		return infEntity;
	}
	
	public static GKInstance createInfGEE(GKInstance geeInst)
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
			//TODO: create_ghost
		}
		return definedSetInst;
	}
	
	public static void createInfComplexPolymer(GKInstance complexInst)
	{
		//TODO: count distinct proteins; filter based on returned protein count and threshold
		GKInstance infComplexInst = createInferredInstance.newInferredGKInstance(complexInst);
		GKInstance complexSummation = new GKInstance(dba.getSchema().getClassByName(ReactomeJavaConstants.Summation));
		try {
			//TODO: Remove brackets from name
			infComplexInst.addAttributeValue(ReactomeJavaConstants.name, complexInst.getAttributeValue("name"));
			complexSummation.addAttributeValue(ReactomeJavaConstants.text, "This complex/polymer has been computationally inferred (based on Ensembl Compara) from a complex/polymer involved in an event that has been demonstrated in another species.");
			infComplexInst.addAttributeValue(ReactomeJavaConstants.summation, complexSummation);
			//TODO: check for identical instances (complexSummation)
			if (complexInst.getSchemClass().isa(ReactomeJavaConstants.Complex))
			{
				for (Object componentInst : complexInst.getAttributeValuesList(ReactomeJavaConstants.hasComponent))
				{
// Current progress spot
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	
	
	public static void createInfEntitySet(GKInstance attributeInst)
	{
		System.out.println("Hello");
	}
}
