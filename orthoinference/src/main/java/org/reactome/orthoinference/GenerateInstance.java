package org.reactome.orthoinference;

import java.util.Collection;
import java.util.HashMap;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;

public class GenerateInstance {
	
		private static MySQLAdaptor dba; 
		private static GKInstance speciesInst = null;
		private static HashMap<String,GKInstance> mockedIdenticals = new HashMap<String,GKInstance>();
	
		public static void setAdaptor(MySQLAdaptor dbAdaptor)
		{
			dba = dbAdaptor;
		}
		
		public static void setSpeciesInst(GKInstance speciesInstCopy)
		{
			speciesInst = speciesInstCopy;
		}
		
		// Creates new instance that will be inferred based on the incoming instances class		
		public static GKInstance newInferredGKInstance(GKInstance instanceToBeInferred) throws Exception
//		TODO: Instance Edits; Valid Attribute comparment/species; check_intracellular 
		{
			GKInstance inferredInst = null;
			String reactionClass = instanceToBeInferred.getSchemClass().getName();
			SchemaClass referenceDNAClass = dba.getSchema().getClassByName(reactionClass);
			inferredInst = new GKInstance(referenceDNAClass);
			inferredInst.setDbAdaptor(dba);
			if (instanceToBeInferred.getSchemClass().isValidAttribute(ReactomeJavaConstants.species) && instanceToBeInferred.getAttributeValue(ReactomeJavaConstants.species) != null)
			{
				inferredInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
			}
			return inferredInst;
		}
		
		// create_ghost equivalent; Returns a mock homologue that is needed in cases of unsuccessful inference
		public static GKInstance newMockGKInstance(GKInstance instanceToBeMocked) throws InvalidAttributeException, InvalidAttributeValueException, Exception
		{
			SchemaClass geeClass = dba.getSchema().getClassByName(ReactomeJavaConstants.GenomeEncodedEntity);
			GKInstance mockedInst = new GKInstance(geeClass);
			mockedInst.setDbAdaptor(dba);
			//TODO: check intracellular; CFII not congruent between Perl and Java
			String mockedName = (String) instanceToBeMocked.getAttributeValue(ReactomeJavaConstants.name);
			mockedInst.addAttributeValue(ReactomeJavaConstants.name, "Ghost homologue of " + mockedName);
			mockedInst.addAttributeValue(ReactomeJavaConstants._displayName, "Ghost homologue of " + instanceToBeMocked.getAttributeValue(ReactomeJavaConstants._displayName));
			mockedInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
			
			// Caching
			String cacheKey = GenerateInstance.getCacheKey((GKSchemaClass) mockedInst.getSchemClass(), mockedInst);
			if (mockedIdenticals.get(cacheKey) != null)
			{
				mockedInst = mockedIdenticals.get(cacheKey);
			} else {
				mockedInst = GenerateInstance.checkForIdenticalInstances(mockedInst);
				mockedIdenticals.put(cacheKey, mockedInst);
			}
			
			if (GenerateInstance.addAttributeValueIfNeccesary(instanceToBeMocked, mockedInst, ReactomeJavaConstants.inferredTo))
			{
				instanceToBeMocked.addAttributeValue(ReactomeJavaConstants.inferredTo, mockedInst);
			}
			dba.updateInstanceAttribute(instanceToBeMocked, ReactomeJavaConstants.inferredTo);
			
			return mockedInst;
		}
		
		// Checks that equivalent instances don't already exist in the DB, substituting if they do
		//TODO: Go over the Perl version and make sure they match perfectly
		public static GKInstance checkForIdenticalInstances(GKInstance inferredInst) throws Exception
		{
			@SuppressWarnings("unchecked")
			Collection<GKInstance> identicalInstances = dba.fetchIdenticalInstances(inferredInst);
			if (identicalInstances != null) {
				if (identicalInstances.size() == 1) {
					return identicalInstances.iterator().next();
				} else if (identicalInstances.size() > 1) {
					return identicalInstances.iterator().next();
				} else {
				return inferredInst;
				}
			} else {
				dba.storeInstance(inferredInst);
				return inferredInst;
			}
		}
		// Checks if the instanceToCheck already contains the instanceToUse in the multi-value attribute
		public static boolean addAttributeValueIfNeccesary(GKInstance instanceToCheck, GKInstance instanceToUse, String attribute) throws InvalidAttributeException, Exception
		{
			for (Object attributeInst : instanceToCheck.getAttributeValuesList(attribute))
			{
				if (attributeInst == instanceToUse) {
					return true;
				}
			}
			return false;
		}
		
		// This function goes through each defining attribute of the incoming instance and produces a string of the attribute values (DB ids if the attribute is an instance)
		@SuppressWarnings("unchecked")
		public static String getCacheKey(GKSchemaClass instClass, GKInstance infInst) throws InvalidAttributeException, Exception
		{
			String key = "";
			
			for (GKSchemaAttribute definingAttr : (Collection<GKSchemaAttribute>) instClass.getDefiningAttributes())
			{
				if (definingAttr.isMultiple()) 
				{
					Collection<Object> multiValueAttributes = infInst.getAttributeValuesList(definingAttr.getName());
					if (multiValueAttributes.size() > 0)
					{
						for (Object attribute : multiValueAttributes)
						{
							if (attribute.getClass().getSimpleName().equals("GKInstance"))
							{
								GKInstance gkInstance = (GKInstance) attribute;
								key += gkInstance.getDBID().toString();
							} else {
								String notInstance = (String) attribute;
								key += notInstance.replaceAll("\\s+", "");
							}
						}
					} else {
						key += "null";
					}
				} else {
					if (definingAttr.isInstanceTypeAttribute() && infInst.getAttributeValue(definingAttr.getName()) != null)
					{
						key += ((GKInstance) infInst.getAttributeValue(definingAttr.getName())).getDBID().toString();
					} else if (infInst.getAttributeValue(definingAttr.getName()) != null) {
						key += infInst.getAttributeValue(definingAttr.getName().toString());
					} else {
						key += "null";
					}
				}
			}
			return key;
		}
}
