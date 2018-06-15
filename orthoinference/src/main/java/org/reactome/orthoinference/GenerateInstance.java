package org.reactome.orthoinference;

import org.gk.model.GKInstance;
import org.gk.model.Instance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

public class GenerateInstance {
	
		private static MySQLAdaptor dba; 
		private static GKInstance speciesInst = null;
	
		public static void setAdaptor(MySQLAdaptor dbAdaptor)
		{
			dba = dbAdaptor;
		}
		
		public void setSpeciesInst(GKInstance speciesInstCopy)
		{
			speciesInst = speciesInstCopy;
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
		
		// create_ghost equivalent; Returns a mock homologue that is needed in cases of unsuccessful inference
		public static GKInstance newMockGKInstance(GKInstance instanceToBeMocked)
		{
			SchemaClass geeClass = dba.getSchema().getClassByName(ReactomeJavaConstants.GenomeEncodedEntity);
			GKInstance mockedInst = new GKInstance(geeClass);
			try {
			String mockedName = (String) instanceToBeMocked.getAttributeValue("name");
			mockedInst.addAttributeValue(ReactomeJavaConstants.name, "Ghost homologue of " + mockedName);
			mockedInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
			//TODO: Instance edit; check intracellular; inferred to/from; update;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return mockedInst;
		}
}
