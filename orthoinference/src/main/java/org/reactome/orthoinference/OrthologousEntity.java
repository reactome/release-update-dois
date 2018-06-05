package org.reactome.orthoinference;

import java.util.ArrayList;

import org.gk.model.GKInstance;

public class OrthologousEntity {

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
		InferEWAS ewasInferrer = new InferEWAS();
		ArrayList<GKInstance> infEWASInstances = ewasInferrer.inferEWAS(attributeInst);
	}
}
