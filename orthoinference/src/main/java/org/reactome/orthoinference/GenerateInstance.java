package org.reactome.orthoinference;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

public class GenerateInstance {
	
		private static MySQLAdaptor dba; 
	
		public static void setAdaptor(MySQLAdaptor dbAdaptor)
		{
			dba = dbAdaptor;
		}
		
		// Creates new instance that will be inferred based on the incoming instances class		
		public GKInstance newInferredGKInstance(GKInstance instanceToBeInferred)
//		TODO: Instance Edits 
//		TODO: Species -> Taxon addition
		{
			GKInstance inferredInst = null;
			try
			{
			String reactionClass = instanceToBeInferred.getSchemClass().getName();
			SchemaClass referenceDNAClass = dba.getSchema().getClassByName(reactionClass);
			inferredInst = new GKInstance(referenceDNAClass);
			
			} catch (Exception e) {
				e.printStackTrace();
			}
			return inferredInst;

		}
		
}
