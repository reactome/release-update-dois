package org.reactome.release.orthopairs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class OrthopairFileGenerator {
    private static final Logger logger = LogManager.getLogger();
    // Create source-target protein mapping file
    public static void createProteinHomologyFile(String sourceTargetProteinMappingFilename, Map<String,Set<String>> speciesProteinHomologs ) throws IOException {

        logger.info("\tGenerating " + sourceTargetProteinMappingFilename);
        File sourceTargetProteinMappingFile = new File(sourceTargetProteinMappingFilename);
        if (sourceTargetProteinMappingFile.exists()) {
            sourceTargetProteinMappingFile.delete();
        }
        sourceTargetProteinMappingFile.createNewFile();

        List<String> sourceProteinIds = new ArrayList<>(speciesProteinHomologs.keySet());
        Collections.sort(sourceProteinIds);
        for (String sourceProteinId : sourceProteinIds) {
            String targetProteinIds = getTargetProteinsAsString(speciesProteinHomologs.get(sourceProteinId));
            String proteinOrthologLine = getProteinId(sourceProteinId);
            proteinOrthologLine += "\t" + targetProteinIds + "\n";
            Files.write(Paths.get(sourceTargetProteinMappingFilename), proteinOrthologLine.getBytes(), StandardOpenOption.APPEND);
        }
    }

    // Create target species gene-protein mapping file
    public static void createSpeciesGeneProteinFile(String speciesKey, String targetGeneProteinMappingFilename, JSONObject speciesJSON, Map<String,Set<String>> speciesGeneProteinMap) throws IOException {

        logger.info("\tGenerating " + targetGeneProteinMappingFilename);
        File targetGeneProteinMappingFile = new File (targetGeneProteinMappingFilename);
        if (targetGeneProteinMappingFile.exists()) {
            targetGeneProteinMappingFile.delete();
        }

        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        if (altIdMappingExists(speciesJSON)) {
            logger.info("\tAlternate ID-Ensembl ID mapping required");
            altIdToEnsemblMap = AlternateIdMapper.getAltIdMappingFile(speciesKey, speciesJSON.get("alt_id_file").toString());
        }
        targetGeneProteinMappingFile.createNewFile();

        List<String> targetGeneProteinLines = new ArrayList<>();
        for (String targetGeneId : speciesGeneProteinMap.keySet()) {
            String[] geneSplit = targetGeneId.split("=");
            String geneSource = geneSplit[0];
            String geneId = geneSplit[geneSplit.length - 1];
            String targetProteinIds = getTargetProteinsAsString(speciesGeneProteinMap.get(targetGeneId));
            List<String> cleanTargetProteinIds = new ArrayList<>();
            if (!targetProteinIds.isEmpty()) {
                if (!geneSource.startsWith("Ensembl") && altIdMappingExists(speciesJSON)) {
                    if (altIdToEnsemblMap.get(geneId) != null) {
                        targetGeneProteinLines.addAll(
                                createGeneProteinLines(altIdToEnsemblMap.get(geneId), targetProteinIds)
                        );
                    }
                } else {
                    targetGeneProteinLines.add(createGeneProteinLine(geneId, targetProteinIds));
                }
            }
        }
        Collections.sort(targetGeneProteinLines);
        for (String targetGeneProteinLine : targetGeneProteinLines) {
            Files.write(Paths.get(targetGeneProteinMappingFilename), targetGeneProteinLine.getBytes(), StandardOpenOption.APPEND);
        }
    }

    private static String getTargetProteinsAsString(Set<String> targetProteins) {
        return targetProteins
                .stream()
                .filter(targetProteinId -> !targetProteinId.equals("LDO"))
                .map(targetProteinId -> getProteinId(targetProteinId))
                .sorted()
                .collect(Collectors.joining(" "));
    }

    private static String getProteinId(String sourceProteinId) {
        return sourceProteinId.split("=")[1];
    }

    private static boolean altIdMappingExists(JSONObject speciesJSON) {
        return speciesJSON.get("alt_id_file") != null;
    }

    private static Set<String> createGeneProteinLines(Set<String> geneIds, String targetProteinIds) {
        return geneIds
                .stream()
                .map(geneId -> createGeneProteinLine(geneId, targetProteinIds))
                .collect(Collectors.toSet());
    }

    private static String createGeneProteinLine(String geneId, String targetProteinIds) {
        return geneId + "\t" + targetProteinIds + "\n";
    }
}
