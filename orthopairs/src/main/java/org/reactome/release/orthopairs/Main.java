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
            if (speciesKey.equals(sourceMappingSpecies)) {
                biomartFilename += "_protein_gene_mapping.txt";
            } else {
                biomartFilename += "_gene_protein_mapping.txt";
                pantherSpeciesNames.add(speciesJSONObject.get("panther_name").toString());
            }
            File biomartFile = new File(biomartFilename);

            if (!biomartFile.exists()) {
                System.out.println("Generating Biomart mapping file for " + speciesName);
                // Build the XML portion of the Biomart query
                String biomartXMLQuery = BiomartUtilities.buildBiomartXML((String) speciesKey, (JSONObject) speciesJSONFile.get(speciesKey), pathToXMLQuery);
                // Query Biomart for gene-protein mappings for species
                List<String> biomartResults = BiomartUtilities.queryBiomart((String) speciesKey, (JSONObject) speciesJSONFile.get(speciesKey), biomartXMLQuery);
                // Generate Orthopairs Mapping Files
                if (speciesKey.equals(sourceMappingSpecies)) {
                    MappingFileGenerator.createSourceProteinGeneMappingFile(biomartResults, (String) speciesKey, speciesName, biomartFile);
                } else {
                    MappingFileGenerator.createTargetGeneProteinMappingFile(biomartResults, (String) speciesKey, speciesName, biomartFile);
                }
            } else {
                System.out.println(biomartFile + " already exists");
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

            BufferedReader br = new BufferedReader(new FileReader(extractedPantherFile));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("HUMAN")) {
                    String[] tabSplit = line.split("\t");
                    String[] speciesSplit = tabSplit[1].split("\\|");
                    if (pantherSpeciesNames.contains(speciesSplit[0])) {
                        //TODO
                    }
                }
            }
        }
    }

}
