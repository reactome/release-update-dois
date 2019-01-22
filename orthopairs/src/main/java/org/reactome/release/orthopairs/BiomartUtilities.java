package org.reactome.release.orthopairs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

// This class handles query building and execution for Ensembl's Biomart

public class BiomartUtilities {


	// This builds the XML query that is used to obtain gene-protein ID maps from various Ensembl Biomart sites (the main one or the protist, fungi, plant sites).
	public static String buildBiomartXML(String speciesName, JSONObject speciesJSON, String pathToXMLQuery) throws IOException {
		
		// Grab values specific to Biomart query for species. For cases where the 'mart_url' or 'mart_virtual_schema' values don't exist, a hard-coded value is used.
		String virtualSchemaName = speciesJSON.get("mart_virtual_schema") != null ? (String) speciesJSON.get("mart_virtual_schema") : "default";
		String datasetName = (String) speciesJSON.get("mart_group");
		
		BufferedReader br = new BufferedReader(new FileReader(pathToXMLQuery));
		
		// XML will always be a single line, so we only need to iterate once
		String biomartXMLQuery = br.readLine();
		
		// Replace placeholder values in base query with species-specific ones
		biomartXMLQuery = biomartXMLQuery.replace("VIRTUAL_SCHEMA_NAME", virtualSchemaName);
		biomartXMLQuery = biomartXMLQuery.replace("DATASET_NAME", datasetName);
		
		// S. cerevisiae information is queried from the main Biomart site, but doesn't have the 'uniprotsptrembl' attribute. 
		// In fact, this returns an error, killing the query. This bit of logic removes it from the query.
		if (speciesName.equals("scer")) {
			biomartXMLQuery = biomartXMLQuery.replace("<Attribute name = \"uniprotsptrembl\" />", "");
		}

		// If alternative IDs are needed for the species, this block will added it to the XML query
		ArrayList pantherGeneDbs = (JSONArray) speciesJSON.get("panther_gene_dbs");
		if (pantherGeneDbs != null) {
			String attributeTag = "<Attribute name = \"PLACEHOLDER\" />";
			for (Object geneDb : pantherGeneDbs) {
				String attributeTagWithDb = attributeTag.replace("PLACEHOLDER", geneDb.toString());
				biomartXMLQuery = biomartXMLQuery.replace("</Dataset>", attributeTagWithDb + "</Dataset>");
			}
		}
		return biomartXMLQuery;
	}

	// This method performs the query that returns the gene-protein mapping for each species
	public static List<String> queryBiomart(String speciesKey, JSONObject speciesJSON, String biomartXMLQuery) throws IOException {

		// Build remainder of query that contains the URL and the XML query
    	String speciesName = (String) ((JSONArray) speciesJSON.get("name")).get(0);
    	String biomartURL = speciesJSON.get("mart_url") != null ? (String) speciesJSON.get("mart_url") : "http://www.ensembl.org/biomart/martservice";
    	String finalBiomartQuery = biomartURL + "?query=" + URLEncoder.encode(biomartXMLQuery, "UTF-8");
    	
    	// Connect to Biomart API
    	URL biomartQueryURL = new URL(finalBiomartQuery);
    	HttpURLConnection connection = (HttpURLConnection) biomartQueryURL.openConnection();

    	// Check response is correct and iterate through it, populating an array with line values
    	// TODO: Can it return 200 but still not contain the body?
    	List<String> biomartQueryResults = new ArrayList<String>();
        if (connection.getResponseCode() == 200) {
        	BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    		
    		String resultsLine;
    		while ((resultsLine = br.readLine()) != null) {
    			biomartQueryResults.add(resultsLine);
    		}
    		br.close();
        } else {
        	System.out.println("Got bad response from " + biomartURL + " for " + speciesName + ": " + connection.getResponseCode() + " " + connection.getResponseMessage());
        }
        
        return biomartQueryResults;
		
	}

}
