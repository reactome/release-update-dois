package org.reactome.release.orthopairs;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
        String MGIFileURL = props.get("MGIFileURL").toString();
        String RGDFileURL = props.get("RGDFileURL").toString();
        String XenbaseFileURL = props.get("XenbaseFileURL").toString();
        String ZFINFileURL = props.get("ZFINFileURL").toString();

        new File(releaseNumber).mkdir();

        // Download and extract homology files from Panther
        List<String> pantherFiles = new ArrayList<String>(Arrays.asList(pantherQfOFilename, pantherHCOPFilename));
        for (String pantherFilename : pantherFiles) {
            downloadAndExtractTarFile(pantherFilename, pantherFilepath);
        }

        // Download ID files from various model organism databases (Mouse Genome Informatics, Rat Genome Database, Xenbase (frog), ZFIN (Zebrafish))
        // HGNC identifier file is downloaded as well.
        List<String> alternativeIdMappingURLs = new ArrayList<>(Arrays.asList(MGIFileURL,RGDFileURL,XenbaseFileURL,ZFINFileURL));
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
        OrthologyFileParser.parsePantherOrthologFiles(pantherFiles, sourceMappingSpecies, speciesJSONFile);
        Map<String,Map<String,Set<String>>> sourceTargetProteinHomologs = OrthologyFileParser.getSourceAndTargetProteinHomologs();
        Map<String,Map<String,Set<String>>> targetGeneProteinMap = OrthologyFileParser.getTargetGeneProteinMap();
        for (Object speciesKey : speciesJSONFile.keySet()) {

            if (!speciesKey.equals(sourceMappingSpecies)) {

                JSONObject speciesJSON = (JSONObject)speciesJSONFile.get(speciesKey);
                String speciesPantherName = speciesJSON.get("panther_name").toString();

                String sourceTargetProteinMappingFilename = releaseNumber + "/" + sourceMappingSpecies + "_" + speciesKey + "_mapping.txt";
                Map<String,Set<String>> speciesProteinHomologs = sourceTargetProteinHomologs.get(speciesPantherName);
                OrthopairFileGenerator.createProteinHomologyFile(speciesKey.toString(), sourceTargetProteinMappingFilename, speciesJSON, speciesProteinHomologs);

                String targetGeneProteinMappingFilename = releaseNumber + "/" + speciesKey + "_gene_protein_mapping.txt";
                Map<String,Set<String>> speciesGeneProteinMap = targetGeneProteinMap.get(speciesPantherName);
                OrthopairFileGenerator.createSpeciesGeneProteinFile(speciesKey.toString(), targetGeneProteinMappingFilename, speciesJSON, speciesGeneProteinMap);
            }
        }
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
}
