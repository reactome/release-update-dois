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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class MappingFileGenerator {

	// Creates the protein-gene mapping file for the species that will be the source for Orthoinference. 
	// Typically this is Human for Reactome and O. sativa (rice) for Plant Reactome.
	public static void createSourceProteinGeneMappingFile(List<String> biomartResults, String sourceSpeciesKey) throws IOException {
		
		Map<String,HashSet<String>> proteinGeneMap = new HashMap<String,HashSet<String>>();
		for (String resultLine : biomartResults) {
			String[] tabSplit = resultLine.split("\t");
			
			// Biomart results should always be 4-column tab-seperated lines
			if (tabSplit.length == 4) {
				String ensemblGene = tabSplit[0];
				String swissprot = tabSplit[1];
				String trembl = tabSplit[2];
				
				// Filters for empty fields in the swissprot (2nd) column
				// If they are occupied, adds to the HashMap with the swissprot (protein) ID as the key and the gene ID to the array in the value
				if (!swissprot.equals("")) {
					if (proteinGeneMap.get(swissprot) != null) {
						proteinGeneMap.get(swissprot).add(ensemblGene);
					} else {
						HashSet<String> firstSwissProtAdded = new HashSet<String>(Arrays.asList(ensemblGene));
						proteinGeneMap.put(swissprot, firstSwissProtAdded);
					}
				}
				
				// Filters for empty fields in the trembl (3rd) column
				// If they are occupied, adds to the HashMap with the trembl(protein) ID as the key and the gene ID to the array in the value
				if (!trembl.equals("")) {
					if (proteinGeneMap.get(trembl) != null) {
						proteinGeneMap.get(trembl).add(ensemblGene);
					} else {
						HashSet<String> firstTremblAdded = new HashSet<String>(Arrays.asList(ensemblGene));
						proteinGeneMap.put(trembl, firstTremblAdded);
					}
				}
			}
		}
		
		// Creates and populates output file for source species
		String filename = sourceSpeciesKey + "_protein_gene_mapping.txt";
		Map<String,ArrayList<String>> valuesSortedProteinGeneMap = new HashMap<String,ArrayList<String>>();
		for (String gene : proteinGeneMap.keySet()) {
			valuesSortedProteinGeneMap.put(gene, sortSet(proteinGeneMap.get(gene)));
		}
		outputMappingFile(filename, valuesSortedProteinGeneMap);
	}

	// Creates the gene-protein mapping file for species that will be inferred to during orthoinference. While this may look
	// similar to the 'createSourceProteinGeneMappingFile' function in this class, the values are actually inverted and the
	// handling of them differs slightly. It felt appropriate to divide these into 2 methods so that it's easier to deconstruct.
	public static void createTargetGeneProteinMappingFile(List<String> biomartResults, String targetSpeciesKey) throws IOException {
		
		Set<String> genes = new HashSet<String>();
		Map<String,HashSet<String>> geneSwissprotMap = new HashMap<String,HashSet<String>>();
		Map<String,HashSet<String>> geneTremblMap = new HashMap<String,HashSet<String>>();
		Map<String,HashSet<String>> geneEnsemblMap = new HashMap<String,HashSet<String>>();
		
		for (String resultLine : biomartResults) {
			String[] tabSplit = resultLine.split("\t");
			
			// Biomart results should always be 4-column tab-seperated lines
			if (tabSplit.length == 4) {
				String ensemblGene = tabSplit[0];
				String swissprot = tabSplit[1];
				String trembl = tabSplit[2];
				String ensemblProtein = tabSplit[3];
				
				// Filters for empty fields in the swissprot (2nd) column
				// Prepends 'SWISS' to the Swissprot identifier to distinguish it during Orthoinference
				if (!swissprot.equals("")) {
					genes.add(ensemblGene);
					if (geneSwissprotMap.get(ensemblGene) != null) {
						geneSwissprotMap.get(ensemblGene).add("SWISS:" + swissprot);
					} else {
						HashSet<String> firstSwissprotAdded = new HashSet<String>(Arrays.asList("SWISS:" + swissprot));
						geneSwissprotMap.put(ensemblGene, firstSwissprotAdded);
					}
				}
				
				// Filters for empty fields in the trembl (3rd) column
				// Prepends 'TREMBL' to the Trembl identifier to distinguish it during Orthoinference
				if (!trembl.equals("")) {
					genes.add(ensemblGene);
					if (geneTremblMap.get(ensemblGene) != null) {
						geneTremblMap.get(ensemblGene).add("TREMBL:" + trembl);
					} else {
						HashSet<String> firstTremblAdded = new HashSet<String>(Arrays.asList("TREMBL:" + trembl));
						geneTremblMap.put(ensemblGene, firstTremblAdded);
					}
				}
				
				// Filters for empty fields in the Ensembl Protein (4th) column
				// Prepends 'ENSP' to the Ensembl Protein identifier to distinguish it during Orthoinference
				if (!ensemblProtein.equals("")) {
					genes.add(ensemblGene);
					if (geneEnsemblMap.get(ensemblGene) != null) {
						geneEnsemblMap.get(ensemblGene).add("ENSP:" + ensemblProtein);
					} else {
						HashSet<String> firstEnsemblAdded = new HashSet<String>(Arrays.asList("ENSP:" + ensemblProtein));
						geneEnsemblMap.put(ensemblGene, firstEnsemblAdded);
					}
				}
			}
		}
		
		// Only one set of identifiers will be kept, and therefore associated with the gene.
		// Priority is SwissProt > Trembl > Ensembl Protein
		// So if any SwissProt identifiers exist the Trembl and Ensembl Protein identifiers are dropped. 
		// If SwissProt identifiers don't exist, then Trembl is used and Ensembl Protein identifiers are dropped. 
		Map<String,ArrayList<String>> geneProteinMap = new HashMap<String,ArrayList<String>>();
		for (String gene : genes) {
			if (geneSwissprotMap.get(gene) != null) {
				geneProteinMap.put(gene, sortSet(geneSwissprotMap.get(gene)));
			} else if (geneTremblMap.get(gene) != null) {
				geneProteinMap.put(gene, sortSet(geneTremblMap.get(gene)));
			} else {
				geneProteinMap.put(gene, sortSet(geneEnsemblMap.get(gene)));
			}
		}
		
		String filename = targetSpeciesKey + "_gene_protein_mapping.txt";
		outputMappingFile(filename, geneProteinMap);
	}
	
	// Values in HashSet are inherently random, so this function converts them to an ArrayList and sorts them
	private static ArrayList<String> sortSet(HashSet<String> valuesSet) {
		
		ArrayList<String> valuesList = new ArrayList<String>(valuesSet);
		Collections.sort(valuesList);
		return valuesList;
	}

	// Outputs the mapping file for both source and target species. Sorts the keys of the incoming map before writing to file. 
	private static void outputMappingFile(String filename, Map<String, ArrayList<String>> biomartMapping) throws IOException {
		
		SortedSet<String> sortedBiomartMapKeys = new TreeSet<String>(biomartMapping.keySet());
		File outputFile = new File(filename);
		if (outputFile.exists()) {
			outputFile.delete();
		}
		outputFile.createNewFile();
		for (String key : sortedBiomartMapKeys) {
			Collections.sort(biomartMapping.get(key));
			String output = key + "\t" + String.join(" ", biomartMapping.get(key)) + "\n";
			Files.write(Paths.get(filename), output.getBytes(), StandardOpenOption.APPEND);
		}
		
	}

}
