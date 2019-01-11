package org.reactome.release.orthopairs;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Main 
{
    final private static String pathToSpeciesConfig = "src/main/resources/Species.json";
    final private static String pathToXMLQuery = "src/main/resources/BiomartQuery.xml";
    
    public static void main( String[] args ) throws FileNotFoundException, IOException, ParseException
    {
        
        JSONParser parser = new JSONParser();
        JSONObject speciesJSONFile = (JSONObject) parser.parse(new FileReader(pathToSpeciesConfig));
        
        // If using an alternative source species, specify the 4-letter code as the first argument
        String sourceMappingSpecies = "";
        if (args.length > 0) {
        	sourceMappingSpecies = args[0];
        } else {
        	sourceMappingSpecies = "hsap";
        }
        
        for (Object speciesKey :  speciesJSONFile.keySet()) {
        	
        	JSONObject speciesJSONObject = (JSONObject) speciesJSONFile.get(speciesKey);
        	String speciesName = (String) ((JSONArray) speciesJSONObject.get("name")).get(0);
        	System.out.println("Generating Biomart mapping file for " + speciesName);
        	// Build the XML portion of the Biomart query
        	String biomartXMLQuery = BiomartUtilities.buildBiomartXML((String) speciesKey, (JSONObject) speciesJSONFile.get(speciesKey), pathToXMLQuery);
        	// Query Biomart for gene-protein mappings for species
        	List<String> biomartResults = BiomartUtilities.queryBiomart((String) speciesKey, (JSONObject) speciesJSONFile.get(speciesKey), biomartXMLQuery);
        	// Generate Orthopairs Mapping Files
        	if (speciesKey.equals(sourceMappingSpecies)) {
        		MappingFileGenerator.createSourceProteinGeneMappingFile(biomartResults, (String) speciesKey, speciesName);
        	} else {
        		MappingFileGenerator.createTargetGeneProteinMappingFile(biomartResults, (String) speciesKey, speciesName);
        	}
        }
        
    }
}
