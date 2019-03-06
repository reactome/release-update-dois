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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Main 
{
    /* Orthopairs has been overhauled completely so that it now gets all Homology and Gene-Protein information from PANTHER (pantherdb.org) instead of Ensembl.
     *
     * In the past, Orthopairs was a 3-step process with the goal of getting Protein Homologs between the source species and all of Reactome's species:
     *  1) Download Gene-Protein information all species in Reactome and Protein-Gene information for the source species (Human in Reactome) from Ensembl's Biomart (An unstable step)
     *  2) Get Gene Homologs between source species and all species in Reactome from Ensembl Compara (A very long step)
     *  3) With the species Gene-Protein file from Biomart and the Gene Homolog file from Compara, map out a Protein homolog file
     *
     *  In this updated version, all of this information comes straight from 2 flat files provided by PANTHER. This makes things much quicker and stabler.
     *  The only twist to this though, is that not all of the species Gene IDs come from Ensembl, but rather there is some that come from organism-specific databases.
     *  The solution has been to use alternative ID mapping files from these organism-specific databases to convert any non-Ensembl IDs to their Ensembl equivalent.
     *
     *  At the end, we have the same two files for each species: The Gene-Protein mapping file specific to the species, and the Protein Homolog file.
     *  All Protein IDs are UniProt (instead of the mix of Ensembl and UniProt) and all Gene IDs match the format used by Ensembl.
     *
     *  These files are much smaller than the older version, since PANTHER annotates which homologs are the 'Least Diverged', meaning we can filter out many that add noise to the dataset.
     *
     */

    private static final Logger logger = LogManager.getLogger();

    public static void main( String[] args ) throws IOException, ParseException {

        String pathToConfig = "";
        String sourceMappingSpecies = "";
        // If using an alternative source species, specify the 4-letter code as the second argument
        //TODO: Better solution for this. What if user wants default config but to change source species?
        if (args.length > 0) {
            pathToConfig = args[0];
            sourceMappingSpecies = args[1];
        } else {
            pathToConfig = "src/main/resources/config.properties";
            sourceMappingSpecies = "hsap";
        }
        Properties props = new Properties();
        props.load(new FileInputStream(pathToConfig));
        // Load all config properties
        String releaseNumber = props.get("releaseNumber").toString();
        String pathToSpeciesConfig = props.get("pathToSpeciesConfig").toString();
        String pantherFilepath = props.get("pantherFileURL").toString();
        String pantherQfOFilename = props.get("pantherQfOFilename").toString();
        String pantherHCOPFilename = props.get("pantherHCOPFilename").toString();
        String MGIFileURL = props.get("MGIFileURL").toString();
        String RGDFileURL = props.get("RGDFileURL").toString();
        String XenbaseFileURL = props.get("XenbaseFileURL").toString();
        String ZFINFileURL = props.get("ZFINFileURL").toString();

        if (releaseNumber.isEmpty()) {
            logger.fatal("Please populate config.properties file with releaseNumber");
            throw new IllegalStateException("No releaseNumber attribute in config.properties");
        }
        new File(releaseNumber).mkdir();

        logger.info("Starting Orthopairs file generation");
        // Download and extract homology files from Panther
        List<String> pantherFiles = new ArrayList<String>(Arrays.asList(pantherQfOFilename, pantherHCOPFilename));
        for (String pantherFilename : pantherFiles) {
            downloadAndExtractTarFile(pantherFilename, pantherFilepath);
        }

        // Download ID files from various model organism databases (Mouse Genome Informatics, Rat Genome Database, Xenbase (frog), ZFIN (Zebrafish))
        // HGNC identifier file is downloaded as well.
        List<String> alternativeIdMappingURLs = new ArrayList<>(Arrays.asList(MGIFileURL,RGDFileURL,XenbaseFileURL,ZFINFileURL));
        for (String altIdURL : alternativeIdMappingURLs) {
            File altIdFilepath = Paths.get(altIdURL).getFileName().toFile();
            if (!altIdFilepath.exists()) {
                logger.info("Downloading " + altIdURL);
                FileUtils.copyURLToFile(new URL(altIdURL), altIdFilepath);
            } else {
                logger.info(altIdFilepath + " already exists");
            }
        }

        JSONParser parser = new JSONParser();
        JSONObject speciesJSONFile = (JSONObject) parser.parse(new FileReader(pathToSpeciesConfig));

        // This method will produce two multi-level Maps, sourceTargetProteinHomologs and targetGeneProteinMap
        // sourceTargetProteinHomologs structure: {TargetSpecies-->{SourceProteinId-->[TargetHomologousProteinIds]}}
        // targetGeneProteinMap structure: {TargetSpecies-->{TargetGeneId-->[targetProteinIds]}}
        // The lower-level structure is a Set to reduce redundancy.
        OrthologyFileParser.parsePantherOrthologFiles(pantherFiles, sourceMappingSpecies, speciesJSONFile);
        Map<String,Map<String,Set<String>>> sourceTargetProteinHomologs = OrthologyFileParser.getSourceAndTargetProteinHomologs();
        Map<String,Map<String,Set<String>>> targetGeneProteinMap = OrthologyFileParser.getTargetGeneProteinMap();
        // Produces the protein homology and species gene-protein files
        for (Object speciesKey : speciesJSONFile.keySet()) {
            // No point in the source species mapping to itself
            if (!speciesKey.equals(sourceMappingSpecies)) {
                JSONObject speciesJSON = (JSONObject)speciesJSONFile.get(speciesKey);
                JSONArray speciesNames = (JSONArray) speciesJSON.get("name");
                logger.info("Attempting to create orthopairs files for " + speciesNames.get(0));
                String speciesPantherName = speciesJSON.get("panther_name").toString();
                // Produces the {sourceSpecies}_{targetspecies}_mapping.txt file
                String sourceTargetProteinMappingFilename = releaseNumber + "/" + sourceMappingSpecies + "_" + speciesKey + "_mapping.txt";
                Map<String,Set<String>> speciesProteinHomologs = sourceTargetProteinHomologs.get(speciesPantherName);
                OrthopairFileGenerator.createProteinHomologyFile(sourceTargetProteinMappingFilename, speciesProteinHomologs);
                // Produces the {targetSpecies}_gene_protein_mapping.txt file
                String targetGeneProteinMappingFilename = releaseNumber + "/" + speciesKey + "_gene_protein_mapping.txt";
                Map<String,Set<String>> speciesGeneProteinMap = targetGeneProteinMap.get(speciesPantherName);
                OrthopairFileGenerator.createSpeciesGeneProteinFile(speciesKey.toString(), targetGeneProteinMappingFilename, speciesJSON, speciesGeneProteinMap);
            }
        }

        removePantherFiles(pantherFiles);
        logger.info("Finished Orthopairs file generation");
    }

    private static void downloadAndExtractTarFile(String pantherFilename, String pantherFilepath) throws IOException {

        URL pantherFileURL = new URL(pantherFilepath + pantherFilename);
        File pantherTarFile = new File(pantherFilename);

        // Download files
        if (!pantherTarFile.exists()) {
            logger.info("Downloading " + pantherFileURL);
            FileUtils.copyURLToFile(pantherFileURL, new File(pantherFilename));
        } else {
            logger.info(pantherTarFile + " already exists");
        }

        // Extract tar files
        File extractedPantherFile = new File(pantherFilename.replace(".tar.gz", ""));
        if (!extractedPantherFile.exists()) {
            logger.info("Extracting " + pantherTarFile);
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
            logger.info(pantherTarFile + " has already been extracted");
        }
    }

    private static void removePantherFiles(List<String> pantherFiles) {
        for (String pantherFile : pantherFiles) {
            String unzippedPantherFile = pantherFile.replace(".tar.gz", "");
            new File(pantherFile).delete();
            new File(unzippedPantherFile).delete();
        }
    }
}
