package org.reactome.release.downloadDirectory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

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
		
		generateSBMLFile();
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
					species.put(stableId, speciesInfo);
					
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
					species.put(stableId, speciesInfo);
					
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
	
	public static void generateSBMLFile() throws IOException {
		
		String compStart="<listOfCompartments>\n";
		String compEnd="</listOfCompartments>\n";
		String compName="<compartment name=";
		
		String speciesStart="<listOfSpecies>\n";
		String speciesEnd="</listOfSpecies>\n";
		String speciesName="<species name=";
		String speciesRef="<speciesReference species=";
		
		String reactionListStart="<listOfReactions>\n";
		String reactionListEnd="</listOfReactions>\n";
		
		String reactionStart="<reaction name=";
		String reactionEnd="</reaction>\n";
		
		String reactantStart="<listOfReactants>\n";
		String reactantEnd="</listOfReactants>\n";
		
		String productStart="<listOfProducts>\n";
		String productEnd="</listOfProducts>\n";
		
		String modifierStart="<listOfModifiers>\n";
		String modifierEnd="</listOfModifiers>\n";
		String modifierSpeciesRef="<modifierSpeciesReference species=";
		
		ArrayList<String> xmlLines = new ArrayList<String>();
		xmlLines.add("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
		xmlLines.add("<sbml xmlns=\"http://www.sbml.org/sbml/level2\" level=\"2\" version=\"1\" xmlns:html=\"http://www.w3.org/1999/xhtml\">\n");
		xmlLines.add("<model name=\"REACTOME\">\n");
		
		xmlLines.add(compStart);
		List<String> compartmentKeys = new ArrayList<String>(compartments.keySet());
		Collections.sort(compartmentKeys);
		for (String key : compartmentKeys) {
			String compartmentLine = compName + "\"c" + key + "\" id=\"c" + compartments.get(key) + "\"/>\n"; 
			xmlLines.add(compartmentLine);
		}

		xmlLines.add(compEnd);
		xmlLines.add(speciesStart);
		
		List<String> speciesKeys = new ArrayList<String>(species.keySet());
		Collections.sort(speciesKeys);
		for (String key : speciesKeys) {
			String formattedName = formatName(species.get(key).get("name"));
			String speciesLine = speciesName + "\"s" + formattedName + "\" compartment=\"c" + species.get(key).get("compartment") + "\" id=\"s" + formattedName + "\" />\n";
			xmlLines.add(speciesLine);
		}
		
		xmlLines.add(speciesEnd);
		xmlLines.add(reactionListStart);
		
		List<String> reactionKeys = new ArrayList<String>(reactions.keySet());
		Collections.sort(reactionKeys);
		for (String key : reactionKeys) {
			String formattedName = formatName(reactions.get(key).get("name").get(0));
			String listReactionsLine = reactionStart + "\"r" + formattedName + "\" id=\"" + key + "\">\n";
			xmlLines.add(listReactionsLine);
			
			if (reactions.get(key).get("Input") != null) {
				xmlLines.add(reactantStart);			
				List<String> reactantListInputs = new ArrayList<String>(reactions.get(key).get("Input"));
				Collections.sort(reactantListInputs);
				HashSet<String> inputLines = new HashSet<String>();
				for (String input : reactantListInputs) {
					String inputLine = speciesRef + "\"s" + formatName(species.get(input).get("name")) + "\" />\n";
					if (!inputLines.contains(inputLine)) {
						xmlLines.add(inputLine);
						inputLines.add(inputLine);
					}
				}
				xmlLines.add(reactantEnd);
			}
			
			if (reactions.get(key).get("Output") != null) {
				xmlLines.add(productStart);
				List<String> reactantListOutputs = new ArrayList<String>(reactions.get(key).get("Output"));
				Collections.sort(reactantListOutputs);
				HashSet<String> outputLines = new HashSet<String>();
				for (String output : reactantListOutputs) {
					String outputLine = speciesRef + "\"s" + formatName(species.get(output).get("name")) + "\" />\n";
					if (!outputLines.contains(outputLine)) {
						xmlLines.add(outputLine);
						outputLines.add(outputLine);
					}
				}
				xmlLines.add(productEnd);
			}
			
			if (reactions.get(key).get("Modifier") != null) {
				xmlLines.add(modifierStart);
				List<String> reactantListCatalysts = new ArrayList<String>(reactions.get(key).get("Modifier"));
				Collections.sort(reactantListCatalysts);
				HashSet<String> catalystLines = new HashSet<String>();
				for (String catalyst : reactantListCatalysts) {
					String catalystLine = modifierSpeciesRef + "\"s" + formatName(species.get(catalyst).get("name")) + "\" />\n";
					if (!catalystLines.contains(catalystLine)) {
						xmlLines.add(catalystLine);
						catalystLines.add(catalystLine);
					}
				}
				xmlLines.add(modifierEnd);
			}
			xmlLines.add(reactionEnd);
		}
		
		xmlLines.add(reactionListEnd);
		xmlLines.add("</model>\n");
		xmlLines.add("</smbl>");
		
		PrintWriter sbmlFile = new PrintWriter("homo_sapiens_java.sbml");
		for (String xml : xmlLines) {
			Files.write(Paths.get("homo_sapiens_java.sbml"), xml.getBytes(), StandardOpenOption.APPEND);
		}
		sbmlFile.close();
	}
}
