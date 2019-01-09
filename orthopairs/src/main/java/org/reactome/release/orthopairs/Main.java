package org.reactome.release.orthopairs;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

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
        
        for (Object speciesKey :  speciesJSONFile.keySet()) {
        	// Build the XML portion of the Biomart query
        	String biomartXMLQuery = BiomartUtilities.buildBiomartXML((String) speciesKey, (JSONObject) speciesJSONFile.get(speciesKey), pathToXMLQuery);
        	// Query Biomart for gene-protein mappings for species
        	BiomartUtilities.queryBiomart((String) speciesKey, (JSONObject) speciesJSONFile.get(speciesKey), biomartXMLQuery);
        }
        
    }
}
