package org.reactome.release.downloadDirectory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

public class SBMLDumper {
	// Initialize data structures used in module
	private static HashMap<String,HashMap<String,ArrayList<String>>> reactions = new HashMap<String,HashMap<String,ArrayList<String>>>();
	private static HashMap<String,String> compartments = new HashMap<String,String>();
	private static HashMap<String,HashMap<String,String>> species = new HashMap<String,HashMap<String,String>>();
	
	// Retrieve all human ReactionlikeEvents
	public static void execute(MySQLAdaptor dba) throws Exception {
		GKInstance human = dba.fetchInstance(48887L);
		Collection<GKInstance> reactionInstances = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent, ReactomeJavaConstants.species, "=", human);
		for (GKInstance reactionInst : reactionInstances) {
			dissectEvent(reactionInst);
		}
	}
	
	// Retrieve input/output/catalyst data for each reaction and update the reactions hashmap
	public static void dissectEvent(GKInstance reactionInst) throws InvalidAttributeException, Exception {
		
		boolean validReaction = false;
		for (GKInstance inputInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(ReactomeJavaConstants.input)) {
			if (inputInst.getAttributeValue(ReactomeJavaConstants.compartment) != null) {
				validReaction = true;
			}
		}
		for (GKInstance outputInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(ReactomeJavaConstants.output)) {
			if (outputInst.getAttributeValue(ReactomeJavaConstants.compartment) != null) {
				validReaction = true;
			}
		}
		
		String id = getId(reactionInst);
		
		// Single name value added to ArrayList. This is needed to comply with the structure of the 'reactions' hashmap
		if (validReaction) {
			HashMap<String,ArrayList<String>> reactionsInfo = new HashMap<String,ArrayList<String>>();
			ArrayList<String> name = new ArrayList<String>(Arrays.asList(reactionInst.getAttributeValue(ReactomeJavaConstants.name).toString()));
			reactionsInfo.put("name", name);
			reactions.put(id, reactionsInfo);
		}
		
		// Update hashmaps with input and output information
		for (String entityType : new ArrayList<String>(Arrays.asList(ReactomeJavaConstants.input, ReactomeJavaConstants.output))) {
			for (GKInstance entityInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList(entityType)) {
				if (entityInst.getAttributeValue(ReactomeJavaConstants.compartment) != null) {
					
					String formattedName = formatName(((GKInstance) entityInst
							.getAttributeValue(ReactomeJavaConstants.compartment))
							.getAttributeValue(ReactomeJavaConstants.name).toString());
					compartments.put(formattedName, getId((GKInstance) entityInst.getAttributeValue(ReactomeJavaConstants.compartment)));
					
					String stableId = getId(entityInst);
					String idWithName = stableId + "_" + entityInst.getAttributeValue(ReactomeJavaConstants.name);
					HashMap<String,String> speciesInfo = new HashMap<String,String>();
					speciesInfo.put("db_id", entityInst.getDBID().toString());
					speciesInfo.put("name", idWithName);
					speciesInfo.put("compartment", formattedName);
					
					String capitalizedEntity = entityType.substring(0,1).toUpperCase() + entityType.substring(1);
					if (reactions.get(id).get(capitalizedEntity) != null) {
						reactions.get(id).get(capitalizedEntity).add(stableId);
					} else {
						ArrayList<String> entityArray = new ArrayList<String>(Arrays.asList(stableId));
						reactions.get(id).put(capitalizedEntity, entityArray);
					}
				}
			}
		}
		// Update hashmaps with catalystActivity information -- Requires checking catalyst's physicalEntity for compartment information
		for (GKInstance catalystInst : (Collection<GKInstance>) reactionInst.getAttributeValuesList("catalystActivity")) {
			if (catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity) != null) {
				GKInstance physicalEntityInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity);
				if (physicalEntityInst.getAttributeValue(ReactomeJavaConstants.compartment) != null) {
					
					String formattedName = formatName(((GKInstance) physicalEntityInst
							.getAttributeValue(ReactomeJavaConstants.compartment))
							.getAttributeValue(ReactomeJavaConstants.name).toString());
					compartments.put(formattedName, getId((GKInstance) physicalEntityInst.getAttributeValue(ReactomeJavaConstants.compartment)));
					
					String stableId = getId(physicalEntityInst);
					String idWithName = stableId + "_" + physicalEntityInst.getAttributeValue(ReactomeJavaConstants.name);
					HashMap<String,String> speciesInfo = new HashMap<String,String>();
					speciesInfo.put("db_id", physicalEntityInst.getDBID().toString());
					speciesInfo.put("name", idWithName);
					speciesInfo.put("compartment", formattedName);
					
					if (reactions.get(id).get("Modifier") != null) {
						reactions.get(id).get("Modifier").add(stableId);
					} else {
						ArrayList<String> entityArray = new ArrayList<String>(Arrays.asList(stableId));
						reactions.get(id).put("Modifier", entityArray);
					}
				}
			}
		}
	}
	
	public static String getId(GKInstance reactionInst) throws InvalidAttributeException, Exception {
		
		String id = null;
		if (reactionInst.getSchemClass().isValidAttribute(ReactomeJavaConstants.stableIdentifier) && reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier) != null) {
			id = ((GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).getAttributeValue(ReactomeJavaConstants._displayName).toString();
		} else {
			id = reactionInst.getDBID().toString();
		}
		return id;
	}
	
	public static String formatName(String unformattedName) {
		String formattedName = unformattedName.replaceAll("([^a-zA-Z0-9])", "_");
		formattedName = formattedName.replaceAll("_+", "_");
		return formattedName;
	}
}
