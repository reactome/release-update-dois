package org.reactome.release.orthopairs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MappingFileGenerator {

	// Creates the protein-gene mapping file for the species that will be the source for Orthoinference. 
	// Typically this is Human for Reactome and O. sativa (rice) for Plant Reactome.
	public static void createSourceProteinGeneMappingFile(List<String> biomartResults, String sourceSpeciesKey) throws IOException {
		
		Map<String,ArrayList<String>> proteinGeneMap = new TreeMap<String,ArrayList<String>>();
		for (String resultLine : biomartResults) {
			String[] tabSplit = resultLine.split("\t");
			
			// Results should always return 4-column tab-seperated lines
			if (tabSplit.length == 4) {
				String gene = tabSplit[0];
				String swissprot = tabSplit[1];
				String trembl = tabSplit[2];
				
				// Filters for empty fields in the swissprot (2nd) column
				// If they are occupied, adds to the HashMap with the swissprot (protein) ID as the key and the gene ID to the array in the value
				if (!swissprot.equals("")) {
					if (proteinGeneMap.get(swissprot) != null) {
						proteinGeneMap.get(swissprot).add(gene);
					} else {
						ArrayList<String> firstSwissProtAdded = new ArrayList<String>(Arrays.asList(gene));
						proteinGeneMap.put(swissprot, firstSwissProtAdded);
					}
				}
				
				// Filters for empty fields in the trembl (3rd) column
				// If they are occupied, adds to the HashMap with the trembl(protein) ID as the key and the gene ID to the array in the value
				if (!trembl.equals("")) {
					if (proteinGeneMap.get(trembl) != null) {
						proteinGeneMap.get(trembl).add(gene);
					} else {
						ArrayList<String> firstTremblAdded = new ArrayList<String>(Arrays.asList(gene));
						proteinGeneMap.put(trembl, firstTremblAdded);
					}
				}
			}
		}
		
		// Creates and populates output file for source species
		String filename = sourceSpeciesKey + "_protein_gene_mapping.txt";
		File outputFile = new File(filename);
		outputFile.createNewFile();
		for (String protein : proteinGeneMap.keySet()) {
			Collections.sort(proteinGeneMap.get(protein));
			String output = protein + "\t" + String.join("\t", proteinGeneMap.get(protein)) + "\n";
			Files.write(Paths.get(filename), output.getBytes(), StandardOpenOption.APPEND);
		}
	}

}
