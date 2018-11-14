package org.reactome.orthoinference;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;


// GenerateInstance is meant to act as a catch-all for functions that are instance-oriented, such as creating, mocking, or identical-checking.
public class InstanceUtilities {
	
	private static MySQLAdaptor dba; 
	private static GKInstance speciesInst;
	private static GKInstance instanceEdit;
	private static HashMap<String,GKInstance> mockedIdenticals = new HashMap<String,GKInstance>();
	
	private static String classFilename;

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
	{
		GKInstance inferredInst = null;
		String reactionClass = instanceToBeInferred.getSchemClass().getName();
		if (reactionClass.matches(ReactomeJavaConstants.ReferenceIsoform)) {
			reactionClass = ReactomeJavaConstants.ReferenceGeneProduct;
		}
		SchemaClass instanceClass = dba.getSchema().getClassByName(reactionClass);
		inferredInst = new GKInstance(instanceClass);
		inferredInst.setDbAdaptor(dba);
		inferredInst.addAttributeValue(ReactomeJavaConstants.created, instanceEdit);
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
		mockedInst.addAttributeValue(ReactomeJavaConstants.created, instanceEdit);
		//TODO: CFII not congruent between Perl and Java
		String mockedName = (String) instanceToBeMocked.getAttributeValue(ReactomeJavaConstants.name);
		mockedInst.addAttributeValue(ReactomeJavaConstants.name, "Ghost homologue of " + mockedName);
		mockedInst.addAttributeValue(ReactomeJavaConstants._displayName, "Ghost homologue of " + instanceToBeMocked.getAttributeValue(ReactomeJavaConstants._displayName));
		mockedInst.addAttributeValue(ReactomeJavaConstants.inferredFrom, instanceToBeMocked);
		mockedInst.addAttributeValue(ReactomeJavaConstants.species, speciesInst);
		mockedInst.addAttributeValue(ReactomeJavaConstants.compartment, instanceToBeMocked.getAttributeValue(ReactomeJavaConstants.compartment));
		
		// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which is slow.
		String cacheKey = InstanceUtilities.getCacheKey((GKSchemaClass) mockedInst.getSchemClass(), mockedInst);
		if (mockedIdenticals.get(cacheKey) != null)
		{
			mockedInst = mockedIdenticals.get(cacheKey);
		} else {
			mockedInst = InstanceUtilities.checkForIdenticalInstances(mockedInst);
			mockedIdenticals.put(cacheKey, mockedInst);
		}

		instanceToBeMocked = InstanceUtilities.addAttributeValueIfNeccesary(instanceToBeMocked, mockedInst, ReactomeJavaConstants.inferredTo);
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
			} else {
				// In future, could iterate through array of returned values. For now, this mimics Perl
				return identicalInstances.iterator().next();
			}
		} else {
			dba.storeInstance(inferredInst);
			return inferredInst;
		}
	}
	// Checks if the instanceToCheck already contains the instanceToUse in the multi-value attribute
	//TODO: Naming of incoming variables isn't entirely correct
	public static GKInstance addAttributeValueIfNeccesary(GKInstance inferredInstance, GKInstance originalInstance, String attribute) throws InvalidAttributeException, Exception
	{
		// Original version of this function had two checks: For 'multivalue attribute' and for 'instance-type object'. 
		// Now we know the only attributes being passed through here are inferredTo, inferredFrom, orthologousEvent, and hasEvent, which are all multivalue attribute classes.
		// We also know that it will always be a GKInstance passed through here (see arguments), so we are able to forgo both checks.
		
		Collection<GKInstance> attributeInstancesFromInferredInstance = inferredInstance.getAttributeValuesList(attribute);
		HashSet<Long> dbIdsFromAttributeInstances = new HashSet<Long>();
		for (GKInstance attributeInstance : attributeInstancesFromInferredInstance) {
			dbIdsFromAttributeInstances.add(attributeInstance.getDBID());
		}
		boolean attributeExists = false;
		for (Long attributeInstanceDbId : dbIdsFromAttributeInstances) {
			if (attributeInstanceDbId == originalInstance.getDBID()) {
				attributeExists = true;
			}
		}
		if (!attributeExists) {
			inferredInstance.addAttributeValue(attribute, originalInstance);
		}
		return inferredInstance;
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
	
	public static void setInstanceEdit(GKInstance instanceEditCopy) 
	{
		instanceEdit = instanceEditCopy;
	}
}
