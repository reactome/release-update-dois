package org.reactome.orthoinference;

import java.util.ArrayList;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

public class OrthologousEntity {
	
	private static MySQLAdaptor dba;
	
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

	public void createOrthoEntity(GKInstance attributeInst)
	{

		if (attributeInst.getSchemClass().isValidAttribute("species"))
		{
		// TODO: has_species function 
			
			if (attributeInst.getSchemClass().isa("GenomeEncodedEntity"))
			{
//				System.out.println("GEE");
				OrthologousEntity.createInfGEE(attributeInst);
			} else if (attributeInst.getSchemClass().isa("Complex"))
			{
//				System.out.println("Complex");
			} else if (attributeInst.getSchemClass().isa("EntitySet"))
			{
//				System.out.println("EntitySet");
			} else if (attributeInst.getSchemClass().isa("SimpleEntity"))
			{
//				System.out.println("SimpleEntity");
			} else {
//				System.out.println("Unknown");
			}
		}
	}
	
	public static void createInfGEE(GKInstance attributeInst)
	{
		if (attributeInst.getSchemClass().toString().contains("GenomeEncodedEntity"))
		{
			return;
		}
		//TODO: %homol_gee; create_ghost;
		InferEWAS ewasInferrer = new InferEWAS();
		ArrayList<GKInstance> infEWASInstances = ewasInferrer.inferEWAS(attributeInst);
		if (infEWASInstances.size() > 1)
		{
			try {
			SchemaClass definedSetClass = dba.getSchema().getClassByName(ReactomeJavaConstants.DefinedSet);
			GKInstance definedSetInst = new GKInstance(definedSetClass);
			// TODO: Instance Edit; Check Intracellular; $opt_filt (??); check for identical instances; add attribute values if necessary - inferredFrom/To; %homol_gee
			String definedSetName = "Homologues of " + attributeInst.getAttributeValue("name");
			definedSetInst.addAttributeValue(ReactomeJavaConstants.name, definedSetName);
			definedSetInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
			definedSetInst.addAttributeValue(ReactomeJavaConstants.hasMember, infEWASInstances);
			} catch (Exception e) {
				e.printStackTrace();
			}	
		} else if (infEWASInstances.size() == 1)
		{
			//TODO: %homol_gee
		} else {
			//TODO: create_ghost
		}
	}
}
