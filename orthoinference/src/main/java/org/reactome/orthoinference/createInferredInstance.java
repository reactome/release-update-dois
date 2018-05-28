package org.reactome.orthoinference;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

public class createInferredInstance {
	
		private static MySQLAdaptor dba; 
	
		public static void setAdaptor(MySQLAdaptor dbAdaptor)
		{
			dba = dbAdaptor;
		}
		
		// Creates new instance that will be inferred based on the incoming instances class
		public Instance newInferredInstance(GKInstance rxn)
//		TODO: Instance Edits 
		{
			Instance infInstance = null;
			try
			{
			String rxnClass = rxn.getSchemClass().toString();
			Long dbId = Long.parseLong(rxn.getAttributeValue("DB_ID").toString());
			infInstance = dba.getInstance(rxnClass, dbId);
			
			} catch (Exception e) {
				e.printStackTrace();
			}
			return infInstance;

		}
}
