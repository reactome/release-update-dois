package org.reactome.release.orthopairs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.json.simple.JSONObject;

public class BiomartQueryBuilder {

	public static String buildBiomartXMLQuery(String speciesName, JSONObject speciesJSON, String pathToXMLQuery) throws IOException {
		
		// Grab values specific to Biomart query for species. For cases where the 'mart_url' or 'mart_virtual_schema' values don't exist, a hard-coded value is used.
		String biomartURL = speciesJSON.get("mart_url") != null ? (String) speciesJSON.get("mart_url") : "http://www.ensembl.org/biomart/martservice";
		String virtualSchemaName = speciesJSON.get("mart_virtual_schema") != null ? (String) speciesJSON.get("mart_virtual_schema") : "default";
		String datasetName = (String) speciesJSON.get("mart_group");
		
		BufferedReader br = new BufferedReader(new FileReader(pathToXMLQuery));
		String baseBiomartQuery = br.readLine();
		
		// Replace placeholder values in query with species-specific ones
		baseBiomartQuery = baseBiomartQuery.replace("VIRTUAL_SCHEMA_NAME", virtualSchemaName);
		baseBiomartQuery = baseBiomartQuery.replace("DATASET_NAME", datasetName);
		
		// S. cerevisiae is queried from the main Biomart site, but doesn't have the 'uniprotsptrembl' attribute. In fact, this returns
		// an error, killing the query. This bit of logic removes it from the query.
		if (speciesName.equals("scer")) {
			baseBiomartQuery = baseBiomartQuery.replace("<Attribute name = \"uniprotsptrembl\" />", "");
		}
		
		String biomartQuery = biomartURL + "?query=" + baseBiomartQuery;
		
		return biomartQuery;
	}

}
