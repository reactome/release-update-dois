package org.reactome.orthoinference;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.persistence.MySQLAdaptor;

public class GenerateInstance {
	
		private static MySQLAdaptor dba; 
	
		public static void setAdaptor(MySQLAdaptor dbAdaptor)
		{
			dba = dbAdaptor;
		}
		
		// Creates new instance that will be inferred based on the incoming instances class
		public Instance newInferredInstance(GKInstance reactionInst)
//		TODO: Instance Edits 
//		TODO: Species -> Taxon addition
		{
			Instance inferredInst = null;
			try
			{
			String reactionClass = reactionInst.getSchemClass().toString();
			Long dbId = Long.parseLong(reactionInst.getAttributeValue("DB_ID").toString());
			inferredInst = dba.getInstance(reactionClass, dbId);
			
			} catch (Exception e) {
				e.printStackTrace();
			}
			return inferredInst;

		}
		
}
