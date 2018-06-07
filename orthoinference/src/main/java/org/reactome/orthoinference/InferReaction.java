package org.reactome.orthoinference;

import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.persistence.MySQLAdaptor;

public class InferReaction {

	private static MySQLAdaptor dba;
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}
	
	// This function mimics the Perl version of InferEvent, inferring attribute instances of input, output, and catalyst activity
	public static void inferEvent(GKInstance reactionInst)
	{
		// TODO: Expand Reaction (??)
		GKInstance inferredReaction = null;
		try {
			
			GenerateInstance createInferredInstance = new GenerateInstance();
			
			String dbId = reactionInst.getAttributeValue("DB_ID").toString();
			if (dbId.equals("68595") || dbId.equals("68610") || dbId.equals("68611") )
			{
				String stableId = reactionInst.getAttributeValue("name").toString();
				System.out.println("Reaction: [" + dbId + "] " + stableId);	
				
				// Creates an instance of the reaction that is about to be inferred
				// SetAdaptor could probably be added to an initial setup 
				GenerateInstance.setAdaptor(dba);
				inferredReaction = createInferredInstance.newInferredGKInstance(reactionInst);
				
				InferReaction.inferAttributes(reactionInst, inferredReaction, "input");
				InferReaction.inferAttributes(reactionInst, inferredReaction, "output");
			InferReaction.inferCatalyst(reactionInst, inferredReaction);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	
	}
	
	// Function used to create inferred instances related to either 'input' or 'output'
	@SuppressWarnings("unchecked")
	public static void inferAttributes(GKInstance reactionInst, Instance inferredReaction, String attribute)
	{
		try {

		for (GKInstance attributeInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(attribute))
		{
//			System.out.println("  " + attribute);
			OrthologousEntity orthologousEntity = new OrthologousEntity();
			orthologousEntity.createOrthoEntity(attributeInst);
		}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Function used to created inferred catalysts
	@SuppressWarnings("unchecked")
	public static void inferCatalyst(GKInstance reactionInst, Instance inferredReaction)
	{
		try {
			
		for (@SuppressWarnings("unused") GKInstance attributeInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList("catalystActivity"))
		{
//			System.out.println("  CatalystActivity");
		}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
