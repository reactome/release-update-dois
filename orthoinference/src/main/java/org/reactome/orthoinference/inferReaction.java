package org.reactome.orthoinference;

import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.persistence.MySQLAdaptor;

public class inferReaction {

	private static MySQLAdaptor dba;
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}
	
	// This function mimics the Perl version of InferEvent, inferring attribute instances of input, output, and catalyst activity
	public static void inferEvent(GKInstance rxn)
	{
		Instance infRxn = null;
		try {
			
			createInferredInstance createInferredInstance = new createInferredInstance();
			
			String dbId = rxn.getAttributeValue("DB_ID").toString();
			String stableId = rxn.getAttributeValue("name").toString();
//			System.out.println("Reaction: [" + dbId + "] " + stableId);	
			
			// Creates an instance of the reaction that is about to be inferred
			// SetAdaptor could probably be added to an initial setup 
			createInferredInstance.setAdaptor(dba);
			infRxn = createInferredInstance.newInferredInstance(rxn);
			
			inferReaction.inferAttributes(rxn, infRxn, "input");
			inferReaction.inferAttributes(rxn, infRxn, "output");
			inferReaction.inferCatalyst(rxn, infRxn);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	
	}
	
	// Function used to create inferred instances related to either 'input' or 'output'
	public static void inferAttributes(GKInstance rxn, Instance infRxn, String attribute)
	{
		try {

		for (GKInstance attrInst : (Collection<GKInstance>) rxn.getAttributeValuesList(attribute))
		{
//			System.out.println("  " + attribute);
			orthologousEntity orthologousEntity = new orthologousEntity();
			orthologousEntity.createOrthoEntity(attrInst);
		}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Function used to created inferred catalysts
	public static void inferCatalyst(GKInstance rxn, Instance infRxn)
	{
		try {
			
		for (GKInstance attrInst : (Collection<GKInstance>) rxn.getAttributeValuesList("catalystActivity"))
		{
//			System.out.println("  CatalystActivity");
		}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
