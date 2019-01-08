package org.reactome.release.orthopairs;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Main 
{
    public static void main( String[] args ) throws FileNotFoundException, IOException, ParseException
    {
        String pathToSpeciesConfig = "src/main/resources/Species.json";
        
        JSONParser parser = new JSONParser();
        JSONObject speciesJSONFile = (JSONObject) parser.parse(new FileReader(pathToSpeciesConfig));
        
        for (Object speciesKey :  speciesJSONFile.keySet()) {
        	BiomartQueryBuilder.buildBiomartXMLQuery((JSONObject) speciesJSONFile.get(speciesKey));
        }
        
    }
}
