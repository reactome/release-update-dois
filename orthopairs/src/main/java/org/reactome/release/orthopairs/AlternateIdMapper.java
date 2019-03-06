package org.reactome.release.orthopairs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

public class AlternateIdMapper {
    private static final Logger logger = LogManager.getLogger();
    // Since we require some species-specific databases, there is a method for mapping the returned files. This wasn't
    // abstractble, considering that the files all have different formats.
    public static Map<String, Set<String>> getAltIdMappingFile(Object speciesKey, String alternateIdFilename) throws IOException {

        File alternateIdFile = new File(alternateIdFilename);
        BufferedReader br = new BufferedReader(new FileReader(alternateIdFile));
        Map<String,Set<String>> altIdToEnsemblMap = new HashMap<>();
        if (speciesKey.equals("mmus")) {
            altIdToEnsemblMap = mapMouseAlternateIds(br);
        } else if (speciesKey.equals("rnor")) {
            altIdToEnsemblMap = mapRatAlternateIds(br);
        } else if (speciesKey.equals("xtro")) {
           altIdToEnsemblMap = mapFrogAlternateIds(br);
        } else if (speciesKey.equals("drer")) {
            altIdToEnsemblMap = mapZebraFishAlternateIds(br);
        } else if (speciesKey.equals("scer")) {
            altIdToEnsemblMap = mapYeastAlternateIds(br);
        } else {
            logger.warn(speciesKey + " does not have a method for mapping its alternate Ids to Ensembl Ids");
        }

        return altIdToEnsemblMap;
    }

    // This uses HGNC_homologene.rpt from http://www.informatics.jax.org/downloads/reports/
    private static Map<String, Set<String>> mapMouseAlternateIds(BufferedReader br) throws IOException {
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        String line;
        int altIdIndex = 0;
        int ensemblIdIndex = 0;
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");
            if (!line.startsWith("MGI:")) {
                for (int i = 0; i < tabSplit.length; i++) {
                    if (tabSplit[i].equals("MGI Accession ID")) {
                        altIdIndex = i;
                    }
                    if (tabSplit[i].equals("Ensembl Gene ID")) {
                        ensemblIdIndex = i;
                    }
                }
            } else {
                //TODO: Check ensemblIdIndex != 0
                String altId = tabSplit[altIdIndex].split(":")[1];
                String ensemblId = tabSplit[ensemblIdIndex];
                if (!ensemblId.equals("null")) {
                    Set<String> firstIdAdded = new HashSet<>(Arrays.asList(ensemblId));
                    altIdToEnsemblMap.put(altId, firstIdAdded);
                }
            }
        }
        return altIdToEnsemblMap;
    }

    // This uses GENES_RAT.txt from ftp://ftp.rgd.mcw.edu/pub/data_release/
    private static Map<String, Set<String>> mapRatAlternateIds(BufferedReader br) throws IOException {
        String line;
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        int altIdIndex = 0;
        int ensemblIdIndex = 0;
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");
            if (line.startsWith("GENE")) {
                for (int i = 0; i<tabSplit.length; i++) {
                    if (tabSplit[i].equals("GENE_RGD_ID")) {
                        altIdIndex = i;
                    }
                    if (tabSplit[i].equals("ENSEMBL_ID")) {
                        ensemblIdIndex = i;
                    }
                }
            } else if (!line.startsWith("#")) {
                boolean ensemblIdColumnExists = true;
                if (tabSplit.length < (ensemblIdIndex + 1)) {
                    ensemblIdColumnExists = false;
                }
                if (ensemblIdColumnExists && !tabSplit[ensemblIdIndex].equals("")) {
                    String altId = tabSplit[altIdIndex];
                    String[] ensemblIds = tabSplit[ensemblIdIndex].split(";");
                    for (int i = 0; i < ensemblIds.length; i++) {
                        String ensemblId = ensemblIds[i];
                        if (altIdToEnsemblMap.get(altId) == null) {
                            Set<String> firstIdAdded = new HashSet<>(Arrays.asList(ensemblId));
                            altIdToEnsemblMap.put(altId, firstIdAdded);
                        } else {
                            altIdToEnsemblMap.get(altId).add(ensemblId);
                        }
                    }
                }
            }
        }
        return altIdToEnsemblMap;
    }

    // This uses GenePageEnsemblModelMapping.txt from ftp://ftp.xenbase.org/pub/GenePageReports/
    private static Map<String,Set<String>> mapFrogAlternateIds(BufferedReader br) throws IOException {
        String line;
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");
            String altId = "";
            String ensemblId = "";
            for (int i = 0; i < tabSplit.length; i++) {
                if (tabSplit[i].startsWith("XB-GENE")) {
                    altId = tabSplit[i];
                }
                if (tabSplit[i].startsWith("ENSXETG")) {
                    ensemblId = tabSplit[i];
                }
            }
            Set<String> firstIdAdded = new HashSet<>(Arrays.asList(ensemblId));
            altIdToEnsemblMap.put(altId, firstIdAdded);
        }
        return altIdToEnsemblMap;
    }

    // This uses ensembl_1_to_1.txt from https://zfin.org/downloads/
    private static Map<String, Set<String>> mapZebraFishAlternateIds(BufferedReader br) throws IOException {
        String line;
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");
            String altId = "";
            String ensemblId = "";
            for (int i = 0; i < tabSplit.length; i++) {
                if (tabSplit[i].startsWith("ZDB-")) {
                    altId = tabSplit[i];
                }
                if (tabSplit[i].startsWith("ENSDARG")) {
                    ensemblId = tabSplit[i];
                }
            }
            if (altIdToEnsemblMap.get(altId) == null) {
                Set<String> firstIdAdded = new HashSet<>(Arrays.asList(ensemblId));
                altIdToEnsemblMap.put(altId, firstIdAdded);
            } else {
                altIdToEnsemblMap.get(altId).add(ensemblId);
            }

        }
        return altIdToEnsemblMap;
    }

    // This uses a static file found in src/main/resources/ -- there is no way to download the file programmatically.
    // We were told that the file shouldn't be changing much considering the yeast genome is well categorized.
    // The file is saved as sgf_ids.txt and was taken from https://yeastmine.yeastgenome.org/yeastmine/bagDetails.do?scope=all&bagName=ALL_Verified_Uncharacterized_Dubious_ORFs
    private static Map<String, Set<String>> mapYeastAlternateIds(BufferedReader br) throws IOException {
        String line;
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");
            String altId = tabSplit[0];
            String ensemblId = tabSplit[1];
            Set<String> firstIdAdded = new HashSet<>(Arrays.asList(ensemblId));
            altIdToEnsemblMap.put(altId, firstIdAdded);
        }
        return altIdToEnsemblMap;
    }
}
