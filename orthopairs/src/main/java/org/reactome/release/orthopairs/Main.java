package org.reactome.release.orthopairs;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        String pantherFilepath = props.get("pantherFileURL").toString();
        String pantherQfOFilename = props.get("pantherQfOFilename").toString();
        String pantherHCOPFilename = props.get("pantherHCOPFilename").toString();
        String HGNCFileURL = props.get("HGNCFileURL").toString();
        String MGIFileURL = props.get("MGIFileURL").toString();
        String RGDFileURL = props.get("RGDFileURL").toString();
        String XenbaseFileURL = props.get("XenbaseFileURL").toString();
        String ZFINFileURL = props.get("ZFINFileURL").toString();

//        new File(releaseNumber).mkdir();

        List<String> pantherFiles = new ArrayList<String>(Arrays.asList(pantherQfOFilename, pantherHCOPFilename));
        for (String pantherFilename : pantherFiles) {
            downloadAndExtractTarFile(pantherFilename, pantherFilepath);
        }

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

        JSONParser parser = new JSONParser();
        JSONObject speciesJSONFile = (JSONObject) parser.parse(new FileReader(pathToSpeciesConfig));

        // If using an alternative source species, specify the 4-letter code as the first argument
        String sourceMappingSpecies = "";
        if (args.length > 0) {
            sourceMappingSpecies = args[0];
        } else {
            sourceMappingSpecies = "hsap";
        }
    }

    private static void downloadAndExtractTarFile(String pantherFilename, String pantherFilepath) throws IOException {

        URL pantherFileURL = new URL(pantherFilepath + pantherFilename);
        File pantherTarFile = new File(pantherFilename);

        if (!pantherTarFile.exists()) {
            System.out.println("Downloading " + pantherFileURL);
            FileUtils.copyURLToFile(pantherFileURL, new File(pantherFilename));
        } else {
            System.out.println(pantherTarFile + " already exists");
        }

        // Download and extract Panther tar files
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
