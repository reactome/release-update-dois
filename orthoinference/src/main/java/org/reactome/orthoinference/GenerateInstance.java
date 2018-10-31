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


// GenerateInstance is meant to act as a catch-all for functions that are instance-oriented, such as creating, mocking, or identical-checking.
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
		SchemaClass instanceClass = dba.getSchema().getClassByName(reactionClass);
		inferredInst = new GKInstance(instanceClass);
		inferredInst.setDbAdaptor(dba);
		if (instanceToBeInferred.getSchemClass().isValidAttribute(ReactomeJavaConstants.compartment) && instanceToBeInferred.getAttributeValue(ReactomeJavaConstants.compartment) != null) {
			for (Object compartmentInst : instanceToBeInferred.getAttributeValuesList(ReactomeJavaConstants.compartment)) {
				GKInstance compartmentInstGK = (GKInstance) compartmentInst;
				if (compartmentInstGK.getSchemClass().isa(ReactomeJavaConstants.Compartment)) {
					inferredInst.addAttributeValue(ReactomeJavaConstants.compartment, compartmentInstGK);

				} else {
					SchemaClass compartmentClass = dba.getSchema().getClassByName(ReactomeJavaConstants.Compartment);
					GKInstance newCompartmentInst = new GKInstance(compartmentClass);
					newCompartmentInst.setDbAdaptor(dba);
					Collection<GKSchemaAttribute> compartmentAttributes = compartmentClass.getAttributes();
					for (GKSchemaAttribute compartmentAttribute : compartmentAttributes) {
						if (!compartmentAttribute.getName().matches("DB_ID") && compartmentInstGK.getAttributeValue(compartmentAttribute.getName()) != null) {
							for (Object attribute : compartmentInstGK.getAttributeValuesList(compartmentAttribute.getName())) {
								newCompartmentInst.addAttributeValue(compartmentAttribute.getName(), attribute);
							}
						}
					}
					newCompartmentInst = checkForIdenticalInstances(newCompartmentInst);
					inferredInst.addAttributeValue(ReactomeJavaConstants.compartment, newCompartmentInst);
				}
			}
		}
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
		
		// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which is slow.
		String cacheKey = GenerateInstance.getCacheKey((GKSchemaClass) mockedInst.getSchemClass(), mockedInst);
		if (mockedIdenticals.get(cacheKey) != null)
		{
			mockedInst = mockedIdenticals.get(cacheKey);
		} else {
			mockedInst = GenerateInstance.checkForIdenticalInstances(mockedInst);
			mockedIdenticals.put(cacheKey, mockedInst);
		}
		
		GenerateInstance.addAttributeValueIfNeccesary(instanceToBeMocked, mockedInst, ReactomeJavaConstants.inferredTo);
		instanceToBeMocked.addAttributeValue(ReactomeJavaConstants.inferredTo, mockedInst);
		dba.updateInstanceAttribute(instanceToBeMocked, ReactomeJavaConstants.inferredTo);
		
		return mockedInst;
	}
	
	// Checks that equivalent instances don't already exist in the DB, substituting if they do
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
	
	// Caching. This function goes through each defining attribute of the incoming instance and produces a string of the attribute values (DB ids if the attribute is an instance).
	// This allows for identical instances held in memory to be used before trying to use fetchIdenticalInstances, which is expensive. 
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
							key += (String) attribute;
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
	
	public static void resetVariables()
	{
		mockedIdenticals = new HashMap<String,GKInstance>();
	}
}
