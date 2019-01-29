package org.reactome.release.orthopairs;

import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class OrthopairFileGenerator {

    // Create source-target protein mapping file
    public static void createProteinHomologyFile(String speciesKey, String sourceTargetProteinMappingFilename, JSONObject speciesJSON, Map<String,Set<String>> speciesProteinHomologs ) throws IOException {

        System.out.println("\tGenerating " + sourceTargetProteinMappingFilename);
        File sourceTargetProteinMappingFile = new File(sourceTargetProteinMappingFilename);
        if (sourceTargetProteinMappingFile.exists()) {
            sourceTargetProteinMappingFile.delete();
        }
        sourceTargetProteinMappingFile.createNewFile();

        List<String> sourceProteinIds = new ArrayList<>();
        sourceProteinIds.addAll(speciesProteinHomologs.keySet());
        Collections.sort(sourceProteinIds);
        for (String sourceProteinId : sourceProteinIds) {
            Set<String> targetProteinIds = speciesProteinHomologs.get(sourceProteinId);
            targetProteinIds.remove("LDO");
            String proteinOrthologLine = sourceProteinId.split("=")[1] + "\t";
            List<String> cleanTargetProteinIds = new ArrayList<>();
            for (String targetProteinId : targetProteinIds) {
                cleanTargetProteinIds.add(targetProteinId.split("=")[1]);
            }
            Collections.sort(cleanTargetProteinIds);
            proteinOrthologLine += String.join(" ", cleanTargetProteinIds) + "\n";
            Files.write(Paths.get(sourceTargetProteinMappingFile.getPath()), proteinOrthologLine.getBytes(), StandardOpenOption.APPEND);
        }
    }

    // Create target species gene-protein mapping file
    public static void createSpeciesGeneProteinFile(String speciesKey, String targetGeneProteinMappingFilename, JSONObject speciesJSON, Map<String,Set<String>> speciesGeneProteinMap) throws IOException {

        System.out.println("\tGenerating " + targetGeneProteinMappingFilename);
        File targetGeneProteinMappingFile = new File (targetGeneProteinMappingFilename);
        if (targetGeneProteinMappingFile.exists()) {
            targetGeneProteinMappingFile.delete();
        }

        boolean altIdMappingExists = false;
        Map<String, Set<String>> altIdToEnsemblMap = new HashMap<>();
        if (speciesJSON.get("alt_id_file") != null) {
            System.out.println("\tAlternate ID-Ensembl ID mapping required");
            altIdToEnsemblMap = AlternateIdMapper.getAltIdMappingFile(speciesKey, speciesJSON.get("alt_id_file").toString());
            altIdMappingExists = true;
        }
        targetGeneProteinMappingFile.createNewFile();

        List<String> targetGeneProteinLines = new ArrayList<>();
        for (String targetGeneId : speciesGeneProteinMap.keySet()) {
            String[] geneSplit = targetGeneId.split("=");
            String geneSource = geneSplit[0];
            String geneId = geneSplit[geneSplit.length - 1];
            Set<String> targetProteinIds = speciesGeneProteinMap.get(targetGeneId);
            targetProteinIds.remove("LDO");
            List<String> cleanTargetProteinIds = new ArrayList<>();
            for (String targetProteinId : speciesGeneProteinMap.get(targetGeneId)) {
                cleanTargetProteinIds.add(targetProteinId.split("=")[1]);
            }
            if (cleanTargetProteinIds.size() > 0) {
                Collections.sort(cleanTargetProteinIds);
                if (!geneSource.startsWith("Ensembl") && altIdMappingExists) {
                    Set<String> ensemblGeneIds = altIdToEnsemblMap.get(geneId);
                    if (ensemblGeneIds != null) {
                        for (String ensemblGeneId : altIdToEnsemblMap.get(geneId)) {
                            String geneProteinLine = ensemblGeneId + "\t" + String.join(" ", cleanTargetProteinIds) + "\n";
                            targetGeneProteinLines.add(geneProteinLine);
                        }
                    }
                } else {
                    String geneProteinLine = geneId + "\t" + String.join(" ", cleanTargetProteinIds) + "\n";
                    targetGeneProteinLines.add(geneProteinLine);
                }
            }
        }
        Collections.sort(targetGeneProteinLines);
        for (String targetGeneProteinLine : targetGeneProteinLines) {
            Files.write(Paths.get(targetGeneProteinMappingFile.getPath()), targetGeneProteinLine.getBytes(), StandardOpenOption.APPEND);
        }
    }
}
