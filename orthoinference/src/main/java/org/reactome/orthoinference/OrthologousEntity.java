package org.reactome.orthoinference;

import org.gk.model.GKInstance;

public class OrthologousEntity {

	public void createOrthoEntity(GKInstance attrInst)
	{

		if (attrInst.getSchemClass().isValidAttribute("species"))
		{
		// TODO: has_species function 
			
			if (attrInst.getSchemClass().isa("GenomeEncodedEntity"))
			{
//				System.out.println("GEE");
				OrthologousEntity.createInfGEE(attrInst);
			} else if (attrInst.getSchemClass().isa("Complex"))
			{
//				System.out.println("Complex");
			} else if (attrInst.getSchemClass().isa("EntitySet"))
			{
//				System.out.println("EntitySet");
			} else if (attrInst.getSchemClass().isa("SimpleEntity"))
			{
//				System.out.println("SimpleEntity");
			} else {
//				System.out.println("Unknown");
			}
		}
	}
	
	public static void createInfGEE(GKInstance attrInst)
	{
		if (attrInst.getSchemClass().toString().contains("GenomeEncodedEntity"))
		{
			return;
		}
		InferEWAS inferEWAS = new InferEWAS();
		inferEWAS.inferEWAS(attrInst);
	}
}
