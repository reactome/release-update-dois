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

        for (String pantherFilename : pantherFiles) {
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

            JSONObject sourceJSONObject = (JSONObject) speciesJSONFile.get(sourceMappingSpecies);
            String sourcePantherName = (String) sourceJSONObject.get("panther_name");

            Map<String, Set<String>> homologGenes = new HashMap<>();
            Map<String, Set<String>> homlogProteins = new HashMap<>();
            Set<String> humanSource = new HashSet<>();
            BufferedReader br = new BufferedReader(new FileReader(extractedPantherFile));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(sourcePantherName)) {
                    String[] tabSplit = line.split("\t");
                    String sourceInfo = tabSplit[0];
                    String targetInfo = tabSplit[1];
                    String orthologType = tabSplit[2];
                    String[] speciesSplit = targetInfo.split("\\|");

                    if (pantherSpeciesNames.contains(speciesSplit[0])) {
                        String speciesName = speciesSplit[0];
                        String[] geneSplit = speciesSplit[1].split("=");
                        String geneDb = geneSplit[0];
                        String geneId = geneSplit[geneSplit.length - 1];
                        String[] proteinSplit = speciesSplit[2].split("=");
                        String proteinDb = proteinSplit[0];
                        String proteinId = proteinSplit[proteinSplit.length - 1];

                        if (homologGenes.get(speciesName) == null) {
                            HashSet<String> speciesGeneDbs = new HashSet<>(Arrays.asList(geneDb));
                            homologGenes.put(speciesName, speciesGeneDbs);
                        } else {
                            homologGenes.get(speciesName).add(geneDb);
                        }

                        String sourceDbInfo = sourceInfo.split("\\|")[1];
                        String sourceDb = sourceDbInfo.split("=")[0];
                    }
                }
            }
        }
    }

}
