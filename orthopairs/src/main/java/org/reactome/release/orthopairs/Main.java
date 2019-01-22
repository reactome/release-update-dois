package org.reactome.release.orthopairs;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Main 
{


    
    public static void main( String[] args ) throws FileNotFoundException, IOException, ParseException {
        String pathToConfig = "";
        if (args.length > 0) {
            pathToConfig = args[0];
        } else {
            pathToConfig = "src/main/resources/config.properties";
        }
        Properties props = new Properties();
        props.load(new FileInputStream(pathToConfig));

        String releaseNumber = props.get("releaseNumber").toString();
        String pathToSpeciesConfig = props.get("pathToSpeciesConfig").toString();
        String pathToXMLQuery = props.get("pathToXMLQuery").toString();
        String pantherFilepath = props.get("pantherFileURL").toString();
        String pantherQfOFilename = props.get("pantherQfOFilename").toString();
        String pantherHCOPFilename = props.get("pantherHCOPFilename").toString();

        List<String> pantherFiles = new ArrayList<String>(Arrays.asList(pantherQfOFilename, pantherHCOPFilename));

        for (String pantherFilename : pantherFiles) {
            URL pantherFileURL = new URL(pantherFilepath + pantherFilename);
            String pantherFilenameWithRelease = releaseNumber + "_" + pantherFilename;
            File pantherFileTar = new File(pantherFilenameWithRelease);

            if (!pantherFileTar.exists()) {
                System.out.println("Downloading " + pantherFileURL);
                FileUtils.copyURLToFile(pantherFileURL, new File(pantherFilenameWithRelease));
            } else {
                System.out.println(pantherFileTar + " already exists");
            }
        }

        JSONParser parser = new JSONParser();
        JSONObject speciesJSONFile = (JSONObject) parser.parse(new FileReader(pathToSpeciesConfig));

        // If using an alternative source species, specify the 4-letter code as the first argument
        String sourceMappingSpecies = "";
        if (args.length > 0) {
            sourceMappingSpecies = args[0];
        } else {
            sourceMappingSpecies = "hsap";
        }

        Set<String> pantherSpeciesNames = new HashSet<>();
        for (Object speciesKey : speciesJSONFile.keySet()) {
            JSONObject speciesJSONObject = (JSONObject) speciesJSONFile.get(speciesKey);
            String speciesName = (String) ((JSONArray) speciesJSONObject.get("name")).get(0);
            String biomartFilename = releaseNumber + "_" + speciesKey;
            String alternativeIdsFilename = releaseNumber + "_" + speciesKey;
            if (speciesKey.equals(sourceMappingSpecies)) {
                biomartFilename += "_protein_gene_mapping.txt";
                alternativeIdsFilename += "_altId_ensembl_mapping.txt";
            } else {
                biomartFilename += "_gene_protein_mapping.txt";
                alternativeIdsFilename += "_altId_ensembl_mapping.txt";
                pantherSpeciesNames.add(speciesJSONObject.get("panther_name").toString());
            }

            File biomartFile = new File(biomartFilename);
            File altIdFile = new File(alternativeIdsFilename);

            boolean altIdFileNeeded = false;
            if (speciesJSONObject.get("panther_gene_dbs") != null && !altIdFile.exists()) {
                altIdFileNeeded = true;
            }

            if (!biomartFile.exists() || altIdFileNeeded) {
                System.out.println("Generating Biomart mapping files for " + speciesName);
                // Build the XML portion of the Biomart query
                String biomartXMLQuery = BiomartUtilities.buildBiomartXML((String) speciesKey, (JSONObject) speciesJSONFile.get(speciesKey), pathToXMLQuery);
                // Query Biomart for gene-protein mappings for species
                List<String> biomartResults = BiomartUtilities.queryBiomart((String) speciesKey, (JSONObject) speciesJSONFile.get(speciesKey), biomartXMLQuery);
                // Generate Orthopairs Mapping Files
                if (speciesKey.equals(sourceMappingSpecies)) {
                    MappingFileGenerator.createSourceProteinGeneMappingFile(biomartResults, (String) speciesKey, speciesName, biomartFile, altIdFile);
                } else {
                    MappingFileGenerator.createTargetGeneProteinMappingFile(biomartResults, (String) speciesKey, speciesName, biomartFile, altIdFile);
                }
            } else {
                System.out.println("Biomart mapping files already exist");
            }
        }

        // PIG is found only in the Ortholog_HCOP file, while the rest are found in the QfO_Genome_Orthologs.
        // We need to go through both files to get all information. The exact same information is found for species
        // found in both files. Sets are used to prevent redundancy.
        for (String pantherFilename : pantherFiles) {
            // First, extract the tar file if needed.
            String pantherFilenameWithRelease = releaseNumber + "_" + pantherFilename;
            File pantherFileTar = new File(pantherFilenameWithRelease);
            File extractedPantherFile = new File(pantherFilename.replace(".tar.gz", ""));
            if (!extractedPantherFile.exists()) {
                InputStream is = new FileInputStream(pantherFileTar);
                GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(is);
                TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn);
                TarArchiveEntry tarFile;
                while ((tarFile = (TarArchiveEntry) tarIn.getNextEntry()) != null) {
                    int count;
                    byte data[] = new byte[1024];
                    FileOutputStream fos = new FileOutputStream(tarFile.getName(), false);
                    try (BufferedOutputStream dest = new BufferedOutputStream(fos, 1024)) {
                        while ((count = tarIn.read(data, 0, 1024)) != -1) {
                            dest.write(data, 0, count);
                        }
                    }
                    //TODO: Version output file
                }
            } else {
                System.out.println("Panther file has already been extracted");
            }

            // Panther has specific names for each organism that are found in the Species config
            JSONObject sourceJSONObject = (JSONObject) speciesJSONFile.get(sourceMappingSpecies);
            String sourceSpeciesName = (String) sourceJSONObject.get("panther_name");

            // Iterate through each line of the file
            Map<String, Map<String,Set<String>>> homologGenes = new HashMap<>();
            Map<String, Set<String>> homlogProteins = new HashMap<>();
            String line;
            BufferedReader br = new BufferedReader(new FileReader(extractedPantherFile));
            while ((line = br.readLine()) != null) {
                // Filter for only Reactome's species
                if (line.startsWith(sourceSpeciesName)) {
                    // Example line: HUMAN|HGNC=10663|UniProtKB=O60524       MOUSE|MGI=MGI=1918305|UniProtKB=Q8CCP0  LDO     Euarchontoglires        PTHR15239
                    // 5 tab-seperated columns. The first and second pertain to the source and target species, respectively. These two columns can be further divided by '|',
                    // giving the species Name, geneId and its source DB, and proteinId and its source DB. The third tab-seperated column gives the ortholog type (LDO = Least Diverged Ortholog, O = Ortholog).
                    // Fourth column is last common ancestor or clade between source and target (verify). Fifth column is Panther ID.
                    // We only parse the first three columns.
                    String[] tabSplit = line.split("\t");
                    String sourceGeneInfo = tabSplit[0].split("\\|")[1];
                    String targetInfo = tabSplit[1];
                    String orthologType = tabSplit[2];
                    String[] targetSplit = targetInfo.split("\\|");

                    // Gene IDs that are labelled with 'Gene|GeneID|Gene_Name|Gene_ORFName|Gene_OrderedLocusName' are not traceable to specific resource, so are ignored.
                    //TODO: This might not apply to Proteins?
                    if (pantherSpeciesNames.contains(targetSplit[0]) && !sourceGeneInfo.startsWith("Gene") && ((orthologType.equals("LDO") || orthologType.equals("O")))) {
                        String targetSpeciesName = targetSplit[0];
                        String targetGeneInfo = targetSplit[1];
                        // Lines with LDO are the most important. If a human gene has an LDO ortholog with the species, then it is the only ortholog we keep.
                        // Rarely there are multiple LDOs, and in those cases we keep both.
                        if (homologGenes.get(targetSpeciesName) == null) {
                            Map<String,Set<String>> firstHumanSpeciesGeneMap = new HashMap<>();
                            Set<String> firstSpeciesGene = new HashSet<>(Arrays.asList(targetGeneInfo));
                            if (orthologType.equals("LDO")) {
                                firstSpeciesGene.add("LDO");
                            }
                            firstHumanSpeciesGeneMap.put(sourceGeneInfo, firstSpeciesGene);
                            homologGenes.put(targetSpeciesName, firstHumanSpeciesGeneMap);
                        } else {
                            if (homologGenes.get(targetSpeciesName).get(sourceGeneInfo) == null) {
                                Set<String> firstSpeciesGene = new HashSet<>(Arrays.asList(targetGeneInfo));
                                if (orthologType.equals("LDO")) {
                                    firstSpeciesGene.add("LDO");
                                }
                                homologGenes.get(targetSpeciesName).put(sourceGeneInfo, firstSpeciesGene);
                            } else {
                                Set<String> speciesGeneSet = homologGenes.get(targetSpeciesName).get(sourceGeneInfo);
                                if (speciesGeneSet.contains("LDO")) {
                                    // Human gene will have multiple LDO's
                                    if (orthologType.equals("LDO")) {
                                        homologGenes.get(targetSpeciesName).get(sourceGeneInfo).add(targetGeneInfo);

                                    }
                                } else if (orthologType.equals("LDO")) {
                                    speciesGeneSet.clear();
                                    speciesGeneSet.add(targetGeneInfo);
                                    speciesGeneSet.add("LDO");
                                    homologGenes.get(targetSpeciesName).put(sourceGeneInfo, speciesGeneSet);
                                } else {
                                    homologGenes.get(targetSpeciesName).get(sourceGeneInfo).add(targetGeneInfo);
                                }
                            }
                        }
                    }
                }
            }

            // Having iterated through both files, we now have two structures: Source species Genes and their target species orthologs, and the protein counterpart.
            // We will now write these results to a file before mapping them with the Biomart files.
        }
    }

}
