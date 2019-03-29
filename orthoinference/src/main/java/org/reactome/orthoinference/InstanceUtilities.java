package org.reactome.orthoinference;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import static org.gk.model.ReactomeJavaConstants.*;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaAttribute;
import org.gk.schema.GKSchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.InvalidAttributeValueException;
import org.gk.schema.SchemaClass;


// GenerateInstance is meant to act as a catch-all for functions that are instance-oriented, such as creating, mocking, or identical-checking.
public class InstanceUtilities {
	
	private static final Logger logger = LogManager.getLogger();
	private static MySQLAdaptor dba; 
	private static GKInstance speciesInst;
	private static GKInstance instanceEditInst;
	private static Map<String,GKInstance> mockedIdenticals = new HashMap<String,GKInstance>();
	
	// Creates new instance that will be inferred based on the incoming instances class		
	public static GKInstance createNewInferredGKInstance(GKInstance instanceToBeInferred) throws Exception
	{
		GKInstance inferredInst = null;
		String reactionClass = instanceToBeInferred.getSchemClass().getName();
		if (reactionClass.matches(ReferenceIsoform)) 
		{
			reactionClass = ReferenceGeneProduct;
		}
		SchemaClass instanceClass = dba.getSchema().getClassByName(reactionClass);
		inferredInst = new GKInstance(instanceClass);
		inferredInst.setDbAdaptor(dba);
		inferredInst.addAttributeValue(created, instanceEditInst);
		if (instanceToBeInferred.getSchemClass().isValidAttribute(compartment) && instanceToBeInferred.getAttributeValue(compartment) != null) 
		{
			for (Object compartmentInst : instanceToBeInferred.getAttributeValuesList(compartment)) 
			{
				GKInstance compartmentInstGk = (GKInstance) compartmentInst;
				if (compartmentInstGk.getSchemClass().isa(Compartment)) 
				{
					inferredInst.addAttributeValue(compartment, compartmentInstGk);
				} else {
					GKInstance newCompartmentInst = createCompartmentInstance(compartmentInstGk);
					inferredInst.addAttributeValue(compartment, newCompartmentInst);
				}
			}
		}
		if (instanceToBeInferred.getSchemClass().isValidAttribute(species) && instanceToBeInferred.getAttributeValue(species) != null)
		{
			inferredInst.addAttributeValue(species, speciesInst);
		}
		return inferredInst;
	}
	
	// Some 'Compartment' instances were actually 'GO_CellularComponent' instances. This meant that the instances that
	// were pulled from the original instance's Compartment attribute could not be added to the new instance, due to them being
	// a GO_CellularComponent. This function is the workaround, producing a Compartment instance that contains all the same attribute values.
	@SuppressWarnings("unchecked")
	public static GKInstance createCompartmentInstance(GKInstance compartmentInstGk) throws InvalidAttributeException, InvalidAttributeValueException, Exception 
	{
		logger.warn(compartmentInstGk + " is a " + compartmentInstGk.getSchemClass() + " instead of a Compartment -- creating new Compartment instance");
		SchemaClass compartmentClass = dba.getSchema().getClassByName(Compartment);
		GKInstance newCompartmentInst = new GKInstance(compartmentClass);
		newCompartmentInst.setDbAdaptor(dba);
		Collection<GKSchemaAttribute> compartmentAttributes = compartmentClass.getAttributes();
		for (GKSchemaAttribute compartmentAttribute : compartmentAttributes) 
		{
			if (!compartmentAttribute.getName().matches("DB_ID") && compartmentInstGk.getAttributeValue(compartmentAttribute.getName()) != null) 
			{
				for (Object attribute : compartmentInstGk.getAttributeValuesList(compartmentAttribute.getName())) 
				{
					newCompartmentInst.addAttributeValue(compartmentAttribute.getName(), attribute);
				}
			}
		}
		newCompartmentInst = checkForIdenticalInstances(newCompartmentInst, null);
		return newCompartmentInst;
	}

	// Equivalent to create_ghost from Perl; Returns a mock homologue that is needed in cases where an inference is rejected, but the
	// component isn't essential for the inference to be completed.
	public static GKInstance createMockGKInstance(GKInstance instanceToBeMocked) throws InvalidAttributeException, InvalidAttributeValueException, Exception
	{
		SchemaClass genomeEncodedEntityClass = dba.getSchema().getClassByName(GenomeEncodedEntity);
		GKInstance mockedInst = new GKInstance(genomeEncodedEntityClass);
		mockedInst.setDbAdaptor(dba);
		mockedInst.addAttributeValue(created, instanceEditInst);
		String mockedInstName = (String) instanceToBeMocked.getAttributeValue(name);
		mockedInst.addAttributeValue(name, "Ghost homologue of " + mockedInstName);
		mockedInst.addAttributeValue(_displayName, "Ghost homologue of " + instanceToBeMocked.getAttributeValue(_displayName));
		mockedInst.addAttributeValue(inferredFrom, instanceToBeMocked);
		mockedInst.addAttributeValue(species, speciesInst);
		mockedInst.addAttributeValue(compartment, instanceToBeMocked.getAttributeValue(compartment));
		
		// Caching based on an instance's defining attributes. This reduces the number of 'checkForIdenticalInstance' calls, which is slow.
		String cacheKey = getCacheKey((GKSchemaClass) mockedInst.getSchemClass(), mockedInst);
		if (mockedIdenticals.get(cacheKey) != null)
		{
			mockedInst = mockedIdenticals.get(cacheKey);
		} else {
			mockedInst = checkForIdenticalInstances(mockedInst, instanceToBeMocked);
			mockedIdenticals.put(cacheKey, mockedInst);
		}
		instanceToBeMocked = addAttributeValueIfNecessary(instanceToBeMocked, mockedInst, inferredTo);
		dba.updateInstanceAttribute(instanceToBeMocked, inferredTo);
		
		return mockedInst;
	}
	
	// Checks that equivalent instances don't already exist in the DB, substituting if they do
	public static GKInstance checkForIdenticalInstances(GKInstance inferredInst, GKInstance originalInst) throws Exception
	{
		@SuppressWarnings("unchecked")
		Collection<GKInstance> identicalInstances = dba.fetchIdenticalInstances(inferredInst);
		if (identicalInstances != null) 
		{			
			logger.info("\tIdentical instances found -- returning " + identicalInstances.iterator().next());
			if (identicalInstances.size() == 1) 
			{
				return identicalInstances.iterator().next();
			} else {
				// TODO: In future, could iterate through array of returned values and pull the 'most identical'. For now, this mimics Perl.
				return identicalInstances.iterator().next();
			}
		} else {
			if (inferredInst.getSchemClass().isa(PhysicalEntity)) {
				inferredInst = EventsInferrer.getStableIdentifierGenerator().generateOrthologousStableId(inferredInst, originalInst);
			}
			dba.storeInstance(inferredInst);
			logger.info("\tNo identical instance found -- inserting " + inferredInst);
			return inferredInst;
		}
	}
	// Checks if the instanceToCheck already contains the instanceToUse in the multi-value attribute
	//TODO: Naming of incoming variables isn't entirely correct -- Not always an inferredInstance first and the original instance second
	@SuppressWarnings("unchecked")
	public static GKInstance addAttributeValueIfNecessary(GKInstance instanceToBeCheckedForExistingAttribute, GKInstance instanceContainingAttributeToBeChecked, String attribute) throws InvalidAttributeException, Exception
	{
		// Original version of this function had two checks: For 'multivalue attribute' and for 'instance-type object'. 
		// Now we know the only attributes being passed through here are inferredTo, inferredFrom, orthologousEvent, and hasEvent, which are all multivalue attribute classes.
		// We also know that it will always be a GKInstance passed through here (see arguments), so we are able to forego both checks.
		Collection<GKInstance> attributeInstancesFromInferredInstance = instanceToBeCheckedForExistingAttribute.getAttributeValuesList(attribute);
		Set<Long> dbIdsFromAttributeInstances = new HashSet<Long>();
		for (GKInstance attributeInstance : attributeInstancesFromInferredInstance) 
		{
			dbIdsFromAttributeInstances.add(attributeInstance.getDBID());
		}
		boolean attributeExists = false;
		for (Long attributeInstanceDbId : dbIdsFromAttributeInstances) 
		{
			if (attributeInstanceDbId == instanceContainingAttributeToBeChecked.getDBID()) {
				attributeExists = true;
			}
		}
		if (!attributeExists) 
		{
			instanceToBeCheckedForExistingAttribute.addAttributeValue(attribute, instanceContainingAttributeToBeChecked);
		}
		return instanceToBeCheckedForExistingAttribute;
	}
	
	// Caching. This function goes through each defining attribute of the incoming instance and produces a string of the attribute values (DB IDs if the attribute is an instance).
	// This allows for identical instances held in memory to be used before trying to use fetchIdenticalInstances, which is expensive. 
	@SuppressWarnings("unchecked")
	public static String getCacheKey(GKSchemaClass instanceClass, GKInstance inferredInst) throws InvalidAttributeException, Exception
	{
		String key = "";
		for (GKSchemaAttribute definingAttr : (Collection<GKSchemaAttribute>) instanceClass.getDefiningAttributes())
		{
			if (definingAttr.isMultiple()) 
			{
				Collection<Object> multiValueAttributes = inferredInst.getAttributeValuesList(definingAttr.getName());
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
				if (definingAttr.isInstanceTypeAttribute() && inferredInst.getAttributeValue(definingAttr.getName()) != null)
				{
					key += ((GKInstance) inferredInst.getAttributeValue(definingAttr.getName())).getDBID().toString();
				} else if (inferredInst.getAttributeValue(definingAttr.getName()) != null) 
				{
					key += inferredInst.getAttributeValue(definingAttr.getName().toString());
				} else {
					key += "null";
				}
			}
		}
		return key;
	}
	
	public static void setAdaptor(MySQLAdaptor dbAdaptor)
	{
		dba = dbAdaptor;
	}
	
	public static void setSpeciesInstance(GKInstance speciesInstCopy)
	{
		speciesInst = speciesInstCopy;
	}
	
	public static void setInstanceEdit(GKInstance instanceEditCopy) 
	{
		instanceEditInst = instanceEditCopy;
	}
	
	public static void resetVariables()
	{
		mockedIdenticals = new HashMap<String,GKInstance>();
	}
	

}
