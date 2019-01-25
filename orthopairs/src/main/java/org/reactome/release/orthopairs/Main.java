package org.reactome.release.orthopairs;

import java.io.*;
import java.net.URL;
import java.util.*;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
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
        String pantherFilepath = props.get("pantherFileURL").toString();
        String pantherQfOFilename = props.get("pantherQfOFilename").toString();
        String pantherHCOPFilename = props.get("pantherHCOPFilename").toString();
        String HGNCFileURL = props.get("HGNCFileURL").toString();
        String MGIFileURL = props.get("MGIFileURL").toString();
        String RGDFileURL = props.get("RGDFileURL").toString();
        String XenbaseFileURL = props.get("XenbaseFileURL").toString();
        String ZFINFileURL = props.get("ZFINFileURL").toString();

//        new File(releaseNumber).mkdir();

        // Download and extract homology files from Panther
        List<String> pantherFiles = new ArrayList<String>(Arrays.asList(pantherQfOFilename, pantherHCOPFilename));
        for (String pantherFilename : pantherFiles) {
            downloadAndExtractTarFile(pantherFilename, pantherFilepath);
        }

        // Download ID files from various model organism databases (Mouse Genome Informatics, Rat Genome Database, Xenbase (frog), ZFIN (Zebrafish))
        // HGNC identifier file is downloaded as well.
        List<String> alternativeIdMappingURLs = new ArrayList<>(Arrays.asList(HGNCFileURL, MGIFileURL,RGDFileURL,XenbaseFileURL,ZFINFileURL));
        for (String altIdURL : alternativeIdMappingURLs) {
            File altIdFilepath = new File(altIdURL.substring(altIdURL.lastIndexOf("/")+1));
            if (!altIdFilepath.exists()) {
                System.out.println("Downloading " + altIdURL);
                FileUtils.copyURLToFile(new URL(altIdURL), altIdFilepath);
            } else {
                System.out.println(altIdFilepath + " already exists");
            }
        }

        // If using an alternative source species, specify the 4-letter code as the first argument
        String sourceMappingSpecies = "";
        if (args.length > 0) {
            sourceMappingSpecies = args[0];
        } else {
            sourceMappingSpecies = "hsap";
        }

        System.out.println();
        JSONParser parser = new JSONParser();
        JSONObject speciesJSONFile = (JSONObject) parser.parse(new FileReader(pathToSpeciesConfig));

        // This method will produce two multi-level Maps, sourceTargetProteinHomologs and targetGeneProteinMap
        // sourceTargetProteinHomologs structure: {TargetSpecies-->{SourceProteinId-->[TargetSpeciesHomologousProteinIds]}}
        // targetGeneProteinMap structure: {TargetSpecies-->{TargetGeneId-->[targetProteinIds]}}
        // The lower-level structure is a Set to reduce redundancy
        Map<String,Map<String,Map<String,Set<String>>>> proteinAndGeneMaps = parsePantherOrthologFiles(pantherFiles, sourceMappingSpecies, speciesJSONFile);
        Map<String,Map<String,Set<String>>> sourceTargetProteinHomologs = proteinAndGeneMaps.get("Protein");
        Map<String,Map<String,Set<String>>> targetGeneProteinMap = proteinAndGeneMaps.get("Gene");
        for (Object speciesKey : speciesJSONFile.keySet()) {
            JSONObject speciesJSON = (JSONObject) speciesJSONFile.get(speciesKey);
            Map<String,Set<String>> altIdToEnsemblMap = new HashMap<>();
            if (speciesJSON.get("alt_id_file") != null) {
                altIdToEnsemblMap = AlternateIdMapper.getAltIdMappingFile(speciesKey, speciesJSON.get("alt_id_file").toString());
            }
        }
    }

    private static Map<String, Map<String, Map<String, Set<String>>>> parsePantherOrthologFiles(List<String> pantherFiles, String sourceMappingSpecies, JSONObject speciesJSONFile) throws IOException {

        // Panther uses different naming conventions for species, which needs to be mapped to Reactome's 4-letter species keys
        Set<String> pantherSpeciesNames = new HashSet<>();
        String sourceSpeciesPantherName = "";
        for (Object speciesKey : speciesJSONFile.keySet()) {
            JSONObject speciesJSON = (JSONObject) speciesJSONFile.get(speciesKey);
            if (!speciesKey.equals(sourceMappingSpecies)) {
                pantherSpeciesNames.add(speciesJSON.get("panther_name").toString());
            } else {
                sourceSpeciesPantherName = speciesJSON.get("panther_name").toString();
            }
        }

        // Ugly 4-level Map that stores the two maps produced, since Java doesn't allow you to return multiple values.
        Map<String,Map<String,Map<String,Set<String>>>> proteinAndGeneMaps = new HashMap<>();
        if (!sourceSpeciesPantherName.equals("")) {
            Map<String,Map<String, Set<String>>> sourceTargetProteinHomologs = new HashMap<>();
            Map<String,Map<String, Set<String>>> targetGeneProteinMap = new HashMap<>();
            // There are 2 Panther files at time of writing since Sus Scrofa (PIG) info is found in a seperate file.
            // The program iterates through both since for overlapping species it produces the same data structure. This is where the redundancy reduction of Sets comes in handy.
            for (String pantherFileTar : pantherFiles) {
                String extractedPantherFile = pantherFileTar.replace(".tar.gz", "");
                String line;
                BufferedReader br = new BufferedReader(new FileReader(extractedPantherFile));
                while ((line = br.readLine()) != null) {
                    // Sample line: HUMAN|HGNC=10663|UniProtKB=O60524	MOUSE|MGI=MGI=1918305|UniProtKB=Q8CCP0	LDO	Euarchontoglires	PTHR15239
                    // Tab-seperated sections: Human gene and protein info, Species gene and protein info, Ortholog type, last common ancestor for homolog (verify), pantherID
                    // For Ortholog type, we only want to look at the Least Divered Ortholog(LDO) and Ortholog(O) lines.
                    if (line.startsWith(sourceSpeciesPantherName)) {
                        String[] tabSplit = line.split("\t");

                        String sourceInfo = tabSplit[0];
                        String[] sourceSplit = sourceInfo.split("\\|");
                        String sourceGene = sourceSplit[1];
                        String sourceProtein = sourceSplit[2];

                        String targetInfo = tabSplit[1];
                        String[] targetSplit = targetInfo.split("\\|");
                        String targetSpecies = targetSplit[0];
                        String targetGene = targetSplit[1];
                        String targetProtein = targetSplit[2];

                        String orthologType = tabSplit[2];

                        // We don't look at lines that contain species that arent in Reactome, or lines where the gene value starts with Gene (Gene|GeneID|Gene_Name|Gene_ORFName|Gene_OrderedLocusName)
                        // since these are often just names, not IDs. Additionally, we only want lines where orthologType is either an 'LDO' or 'O', and not a 'P', 'X', or 'LDX'.
                        if (pantherSpeciesNames.contains(targetSpecies) && !sourceGene.startsWith("Gene") && !targetGene.startsWith("Gene") && orthologType.contains("O")) {
                            sourceTargetProteinHomologs = MapId(targetSpecies, sourceProtein, targetProtein, sourceTargetProteinHomologs, orthologType);
                            targetGeneProteinMap = MapId(targetSpecies, targetGene, targetProtein, targetGeneProteinMap, orthologType);
                        }

                    }
                }
                br.close();
            }
            // Obtuse way of returning the two Maps
            proteinAndGeneMaps.put("Protein", sourceTargetProteinHomologs);
            proteinAndGeneMaps.put("Gene", targetGeneProteinMap);
            return proteinAndGeneMaps;
        } else {
            System.out.println("Could not find source species in Species.json");
            return proteinAndGeneMaps;
        }
    }

    private static Map<String, Map<String, Set<String>>> MapId(String targetSpecies, String keyEntity, String valueEntity, Map<String,Map<String,Set<String>>> entityMap, String orthologType) {

        // The 'null' conditionals are for the first time that level of the map is added. For example, if 'MOUSE' doesn't have an existing structure in the map,
        // the first condition will handle this.
        if (entityMap.get(targetSpecies) == null) {
            Set<String>  firstTargetEntityIdAdded = new HashSet<>(Arrays.asList(valueEntity));
            Map<String,Set<String>> firstSourceEntityMap = new HashMap<>();
            firstSourceEntityMap.put(keyEntity, firstTargetEntityIdAdded);
            entityMap.put(targetSpecies, firstSourceEntityMap);
        } else {
            if (entityMap.get(targetSpecies).get(keyEntity) == null) {
                Set<String> firstTargetEntityIdAdded = new HashSet<>(Arrays.asList(valueEntity));
                entityMap.get(targetSpecies).put(keyEntity, firstTargetEntityIdAdded);
            } else {
                entityMap.get(targetSpecies).get(keyEntity).add(valueEntity);
            }
        }

        // Lines with an orthologType equal to 'LDO' mean that we only want that value in the Set, since its the Least Diverged Ortholog, meaning we have a
        // high degree of confidence in its homology. We remove all other values from the Set unless an 'LDO' value already exists. In this rare case,
        // we will keep multiple LDOs.
        Set<String> targetEntitys = entityMap.get(targetSpecies).get(keyEntity);
        if (orthologType.equals("LDO")) {
            if (!targetEntitys.contains("LDO")) {
                targetEntitys.clear();
                targetEntitys.add(valueEntity);
                targetEntitys.add("LDO");
                entityMap.get(targetSpecies).put(keyEntity, targetEntitys);
            }
        } else if (orthologType.equals("O") && targetEntitys.contains("LDO")) {
            targetEntitys.remove(valueEntity);
            entityMap.get(targetSpecies).put(keyEntity, targetEntitys);
        }
        return entityMap;
    }

    private static void downloadAndExtractTarFile(String pantherFilename, String pantherFilepath) throws IOException {

        URL pantherFileURL = new URL(pantherFilepath + pantherFilename);
        File pantherTarFile = new File(pantherFilename);

        // Download files
        if (!pantherTarFile.exists()) {
            System.out.println("Downloading " + pantherFileURL);
            FileUtils.copyURLToFile(pantherFileURL, new File(pantherFilename));
        } else {
            System.out.println(pantherTarFile + " already exists");
        }

        // Extract tar files
        File extractedPantherFile = new File(pantherFilename.replace(".tar.gz", ""));
        if (!extractedPantherFile.exists()) {
            System.out.println("Extracting " + pantherTarFile);
            TarArchiveInputStream tarIn = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(pantherTarFile)));
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
            }
        } else {
            System.out.println(pantherTarFile + " has already been extracted");
        }
    }
//        Set<String> pantherSpeciesNames = new HashSet<>();
//        Map<String,String> pantherReactomeSpeciesNames = new HashMap<>();
//        for (Object speciesKey : speciesJSONFile.keySet()) {
//            JSONObject speciesJSONObject = (JSONObject) speciesJSONFile.get(speciesKey);
//            String speciesName = (String) ((JSONArray) speciesJSONObject.get("name")).get(0);
//            String biomartFilename = releaseNumber + "_" + speciesKey;
//            String alternativeIdsFilename = releaseNumber + "_" + speciesKey;
//            if (speciesKey.equals(sourceMappingSpecies)) {
//                biomartFilename += "_protein_gene_mapping.txt";
//                alternativeIdsFilename += "_altId_ensembl_mapping.txt";
//            } else {
//                biomartFilename += "_gene_protein_mapping.txt";
//                alternativeIdsFilename += "_altId_ensembl_mapping.txt";
//                pantherSpeciesNames.add(speciesJSONObject.get("panther_name").toString());
//                pantherReactomeSpeciesNames.put(speciesJSONObject.get("panther_name").toString(), speciesKey.toString());
//            }
//
//            File biomartFile = new File(biomartFilename);
//            File altIdFile = new File(alternativeIdsFilename);
//
//            boolean altIdFileNeeded = false;
//            if (speciesJSONObject.get("panther_gene_dbs") != null && !altIdFile.exists()) {
//                altIdFileNeeded = true;
//            }
//
//            if (!biomartFile.exists() || altIdFileNeeded) {
//                System.out.println("Generating Biomart mapping files for " + speciesName);
//                // Build the XML portion of the Biomart query
//                String biomartXMLQuery = BiomartUtilities.buildBiomartXML((String) speciesKey, (JSONObject) speciesJSONFile.get(speciesKey), pathToXMLQuery);
//                // Query Biomart for gene-protein mappings for species
//                List<String> biomartResults = BiomartUtilities.queryBiomart((String) speciesKey, (JSONObject) speciesJSONFile.get(speciesKey), biomartXMLQuery);
//                // Generate Orthopairs Mapping Files
//                if (speciesKey.equals(sourceMappingSpecies)) {
//                    MappingFileGenerator.createSourceProteinGeneMappingFile(biomartResults, (String) speciesKey, speciesName, biomartFile, altIdFile);
//                } else {
//                    MappingFileGenerator.createTargetGeneProteinMappingFile(biomartResults, (String) speciesKey, speciesName, biomartFile, altIdFile);
//                }
//            } else {
//                System.out.println("Biomart mapping files already exist");
//            }
//        }
//
//        // PIG is found only in the Ortholog_HCOP file, while the rest are found in the QfO_Genome_Orthologs.
//        // We need to go through both files to get all information. The exact same information is found for species
//        // found in both files. Sets are used to prevent redundancy.
//        Map<String, Map<String,Set<String>>> homologProteins = new HashMap<>();
//        for (String pantherFilename : pantherFiles) {
//            // First, extract the tar file if needed.
//            String pantherFilenameWithRelease = releaseNumber + "_" + pantherFilename;
//            File pantherFileTar = new File(pantherFilenameWithRelease);
//            File extractedPantherFile = new File(pantherFilename.replace(".tar.gz", ""));
//            if (!extractedPantherFile.exists()) {
//                InputStream is = new FileInputStream(pantherFileTar);
//                GzipCompressorInputStream gzipIn = new GzipCompressorInputStream(is);
//                TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn);
//                TarArchiveEntry tarFile;
//                while ((tarFile = (TarArchiveEntry) tarIn.getNextEntry()) != null) {
//                    int count;
//                    byte data[] = new byte[1024];
//                    FileOutputStream fos = new FileOutputStream(tarFile.getName(), false);
//                    try (BufferedOutputStream dest = new BufferedOutputStream(fos, 1024)) {
//                        while ((count = tarIn.read(data, 0, 1024)) != -1) {
//                            dest.write(data, 0, count);
//                        }
//                    }
//                    //TODO: Version output file
//                }
//            } else {
//                System.out.println("Panther file has already been extracted");
//            }
//
//            // Panther has specific names for each organism that are found in the Species config
//            JSONObject sourceJSONObject = (JSONObject) speciesJSONFile.get(sourceMappingSpecies);
//            String sourceSpeciesName = (String) sourceJSONObject.get("panther_name");
//
//            // Iterate through each line of the file
//            String line;
//            BufferedReader br = new BufferedReader(new FileReader(extractedPantherFile));
//            while ((line = br.readLine()) != null) {
//                // Filter for only Reactome's species
//                if (line.startsWith(sourceSpeciesName)) {
//                    // Example line: HUMAN|HGNC=10663|UniProtKB=O60524       MOUSE|MGI=MGI=1918305|UniProtKB=Q8CCP0  LDO     Euarchontoglires        PTHR15239
//                    // 5 tab-seperated columns. The first and second pertain to the source and target species, respectively. These two columns can be further divided by '|',
//                    // giving the species Name, geneId and its source DB, and proteinId and its source DB. The third tab-seperated column gives the ortholog type (LDO = Least Diverged Ortholog, O = Ortholog).
//                    // Fourth column is last common ancestor or clade between source and target (verify). Fifth column is Panther ID.
//                    // We only parse the first three columns.
//                    String[] tabSplit = line.split("\t");
//                    String[] sourceInfo = tabSplit[0].split("\\|");
//                    String sourceGeneInfo = sourceInfo[1];
//                    String sourceProtein = sourceInfo[2].split("=")[1];
//                    String targetInfo = tabSplit[1];
//                    String[] targetSplit = targetInfo.split("\\|");
//                    String targetGeneInfo = targetSplit[1];
//                    String orthologType = tabSplit[2];
//
//                    // Gene IDs that are labelled with 'Gene|GeneID|Gene_Name|Gene_ORFName|Gene_OrderedLocusName' are not traceable to specific resource, so are ignored.
//                    if (pantherSpeciesNames.contains(targetSplit[0]) && !sourceGeneInfo.startsWith("Gene") && !targetGeneInfo.startsWith("Gene") && (orthologType.equals("LDO") || orthologType.equals("O"))) {
//                        String targetSpeciesName = targetSplit[0];
//                        String targetProtein = targetSplit[2].split("=")[1];
//                        if (targetSpeciesName.equals("RAT")) {
//                            System.out.println(line);
//                        }
//                        // Lines with LDO are the most important. If a human gene has an LDO ortholog with the species, then it is the only ortholog we keep.
//                        // Rarely there are multiple LDOs, and in those cases we keep both.
//                        if (homologProteins.get(targetSpeciesName) == null) {
//                            Map<String,Set<String>> firstHumanSpeciesProteinMap = new HashMap<>();
//                            Set<String> firstSpeciesProtein = new HashSet<>(Arrays.asList(targetProtein));
//                            if (orthologType.equals("LDO")) {
//                                firstSpeciesProtein.add("LDO");
//                            }
//                            firstHumanSpeciesProteinMap.put(sourceProtein, firstSpeciesProtein);
//                            homologProteins.put(targetSpeciesName, firstHumanSpeciesProteinMap);
//                        } else {
//                            if (homologProteins.get(targetSpeciesName).get(sourceProtein) == null) {
//                                Set<String> firstSpeciesProtein = new HashSet<>(Arrays.asList(targetProtein));
//                                if (orthologType.equals("LDO")) {
//                                    firstSpeciesProtein.add("LDO");
//                                }
//                                homologProteins.get(targetSpeciesName).put(sourceProtein, firstSpeciesProtein);
//                            } else {
//                                Set<String> speciesProteinSet = homologProteins.get(targetSpeciesName).get(sourceProtein);
//                                if (speciesProteinSet.contains("LDO")) {
//                                    // Human gene will have multiple LDO's
//                                    if (orthologType.equals("LDO")) {
//                                        homologProteins.get(targetSpeciesName).get(sourceProtein).add(targetProtein);
//                                    }
//                                } else if (orthologType.equals("LDO")) {
//                                    speciesProteinSet.clear();
//                                    speciesProteinSet.add(targetProtein);
//                                    speciesProteinSet.add("LDO");
//                                    homologProteins.get(targetSpeciesName).put(sourceProtein, speciesProteinSet);
//                                } else {
//                                    homologProteins.get(targetSpeciesName).get(sourceProtein).add(targetProtein);
//                                }
//                            }
//                        }
//                    }
//                    //
//                }
//            }
//            System.exit(0);
//            br.close();
//        }
//        // Having iterated through both files, we now have two structures: Source species Genes and their target species orthologs, and the protein counterpart.
//        // We will now write these results to a file before mapping them with the Biomart files.
//        int totalTotal = 0;
//        int hitTotal = 0;
//        int missTotal = 0;
//        for (String speciesKey : homologProteins.keySet()) {
//            speciesKey = "DANRE";
//            String biomartMappingFilename = releaseNumber + "_" + pantherReactomeSpeciesNames.get(speciesKey) + "_gene_protein_mapping.txt";
//            File biomartMappingFile = new File(biomartMappingFilename);
//            Map<String,String> uniprotSources = new HashMap<>();
//            String line;
//            int count = 0;
//            BufferedReader br = new BufferedReader(new FileReader(biomartMappingFile));
//            while ((line = br.readLine()) != null) {
//                count++;
//                String[] tabSplit = line.split("\t");
//                String sourceProtein = tabSplit[0];
//                String[] targetProteinsWithDb = tabSplit[1].split(" ");
//
//                for (String targetProteinWithDb : targetProteinsWithDb) {
//                    String[] proteinAndDb = targetProteinWithDb.split(":");
//                    String proteinId = proteinAndDb[1];
//                    String proteinDb = proteinAndDb[0];
//                    uniprotSources.put(proteinId, proteinDb);
//                }
//            }
//            br.close();
//            List<String> sourceTargetProteinList = new ArrayList<>();
//            int hitCount = 0;
//            int missCount = 0;
//            int ldoMissCount = 0;
//            System.out.println(speciesKey);
//            for (String sourceProtein : homologProteins.get(speciesKey).keySet()) {
//                List<String> updatedTargetProteins = new ArrayList<>();
////                homologProteins.get(speciesKey).get(sourceProtein).remove("LDO");
//                for (String targetProtein : homologProteins.get(speciesKey).get(sourceProtein)) {
//                    String proteinSource = uniprotSources.get(targetProtein);
//                    if (proteinSource == null && !targetProtein.equals("LDO")) {
//                        if (homologProteins.get(speciesKey).get(sourceProtein).contains("LDO")) {
//                            System.out.println(speciesKey + ": " + targetProtein + " ~~~~ " + "Human: " + sourceProtein);
//                            ldoMissCount++;
//                        }
//                        if (!targetProtein.equals("LDO")) {
//                            missCount++;
//                        }
//                    } else {
//                        if (!targetProtein.equals("LDO")) {
//                            hitCount++;
//                        }
//                    }
//                }
//            }
//            int total = hitCount + missCount;
//            float ratio = (float) hitCount/total*100;
//            float missRatio = (float) ldoMissCount/missCount*100;
//            totalTotal += total;
//            hitTotal += hitCount;
//            missTotal += missCount;
//            System.out.println("\tHits:        " + hitCount);
//            System.out.println("\tLDO Misses:  " + ldoMissCount + " (" + missRatio + "%)");
//            System.out.println("\tMisses:      " + missCount);
//            System.out.println("\tTotal:       " + total + " (" + ratio + "%)");
////            System.out.println("\tBiomart Total: " + count);
//        System.exit(0);
//        }
//        float totalRatio = (float) hitTotal/totalTotal*100;
//        System.out.println("Total Hits:   " + hitTotal);
//        System.out.println("Total Misses: " + missTotal);
//        System.out.println("Total total:  " + totalTotal + " (" + totalRatio + "%)");
//    }

}
