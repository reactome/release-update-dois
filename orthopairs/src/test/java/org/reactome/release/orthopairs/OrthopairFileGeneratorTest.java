package org.reactome.release.orthopairs;

import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OrthopairFileGenerator.class, AlternateIdMapper.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})
public class OrthopairFileGeneratorTest {

    private Map<String, Set<String>> mockMap = new HashMap<>();
    private Set<String> mockSet = new HashSet<>();
    private Map<String, Set<String>> mockAltIdMap = new HashMap<>();

    @Mock
    JSONObject mockJSONObject;

//    @Before
//    public void setUp() {
//        MockitoAnnotations.initMocks(this);
//    }

    @Test
    public void createProteinHomologyFileTest() throws IOException {
        String sourceTargetProteinMappingFilename = "sourceTargetProteinMappingFilename";
        mockSet.add("UniProtKB=Q6DEX5");
        mockSet.add("LDO");
        mockMap.put("UniProtKB=Q86YI8", mockSet);
        OrthopairFileGenerator.createProteinHomologyFile( sourceTargetProteinMappingFilename, mockMap);
        BufferedReader br = new BufferedReader(new FileReader(sourceTargetProteinMappingFilename));
        String line;
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");
            assertEquals(tabSplit.length, 2);
            assertEquals(tabSplit[0], "Q86YI8");
            assertEquals(tabSplit[1], "Q6DEX5");
        }
        br.close();
        Files.delete(Paths.get(sourceTargetProteinMappingFilename));
    }

    @Test
    public void createSpeciesGeneProteinFileTest() throws IOException {
        mockSet.add("UniProtKB=F6UIU7");
        mockSet.add("LDO");
        mockMap.put("XB-GENE-973992", mockSet);

        Map<String, Set<String>> mockAltIdMap = new HashMap<>();
        mockAltIdMap.put("XB-GENE-973992", Collections.singleton("ENSXETG00000010038"));

        PowerMockito.mockStatic(AlternateIdMapper.class);
        String targetGeneProteinMappingFilename = "targetGeneProteinMapingFilename";
        Mockito.when(mockJSONObject.get("alt_id_file")).thenReturn("test.txt");
        PowerMockito.when(AlternateIdMapper.getAltIdMappingFile("xtro", "test.txt")).thenReturn(mockAltIdMap);
        OrthopairFileGenerator.createSpeciesGeneProteinFile("xtro", targetGeneProteinMappingFilename, mockJSONObject, mockMap);

        BufferedReader br = new BufferedReader(new FileReader(targetGeneProteinMappingFilename));
        String line;
        while ((line = br.readLine()) != null) {
            String[] tabSplit = line.split("\t");
            assertEquals(tabSplit.length, 2);
            assertEquals(tabSplit[0], "ENSXETG00000010038");
            assertEquals(tabSplit[1], "F6UIU7");
        }
        br.close();
        Files.delete(Paths.get(targetGeneProteinMappingFilename));
    }

    @Test
    public void createSpeciesGeneProteinFileNullEnsemblIdsTest() throws IOException {
        mockSet.add("UniProtKB=F6UIU7");
        mockSet.add("LDO");
        mockMap.put("XB-GENE-973992", mockSet);

        Map<String, Set<String>> nullAltIdMap = new HashMap<>();

        PowerMockito.mockStatic(AlternateIdMapper.class);
        String targetGeneProteinMappingFilename = "targetGeneProteinMapingFilename";
        Mockito.when(mockJSONObject.get("alt_id_file")).thenReturn("test.txt");
        PowerMockito.when(AlternateIdMapper.getAltIdMappingFile("xtro", "test.txt")).thenReturn(nullAltIdMap);

        OrthopairFileGenerator.createSpeciesGeneProteinFile("xtro", targetGeneProteinMappingFilename, mockJSONObject, mockMap);
        Files.delete(Paths.get(targetGeneProteinMappingFilename));
    }

}