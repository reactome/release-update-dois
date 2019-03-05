package org.reactome.release.orthopairs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AlternateIdMapper.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})
public class AlternateIdMapperTest {

    String mouseTestFilename = "src/test/resources/mmus_alt_id_test.txt";
    String ratTestFilename = "src/test/resources/rnor_alt_id_test.txt";
    String frogTestFilename = "src/test/resources/xtro_alt_id_test.txt";
    String zebrafishTestFilename = "src/test/resources/drer_alt_id_test.txt";
    String yeastTestFilename = "src/test/resources/scer_alt_id_test.txt";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void mapMouseAlternateIdsTest() throws IOException {

        Map<String, Set<String>> mouseAltIds = AlternateIdMapper.getAltIdMappingFile("mmus", mouseTestFilename);
        assertEquals(mouseAltIds.get("87859").toString(), "[ENSMUSG00000026842]");
        assertEquals(mouseAltIds.size(), 3);
    }

    @Test
    public void mapRatAlternateIdsTest() throws IOException {

        Map<String, Set<String>> ratAltIds = AlternateIdMapper.getAltIdMappingFile("rnor", ratTestFilename);
        assertEquals(ratAltIds.get("1303072").toString(),"[ENSRNOG00000000478]");
        assertEquals(ratAltIds.size(), 5);
    }

    @Test
    public void mapFrogAlternateIdsTest() throws IOException {

        Map<String, Set<String>> frogAltIds = AlternateIdMapper.getAltIdMappingFile("xtro", frogTestFilename);
        assertEquals(frogAltIds.get("XB-GENE-478094").toString(), "[ENSXETG00000024397]");
        assertEquals(frogAltIds.size(), 5);
    }

    @Test
    public void mapZebrafishAlternateIdsTest() throws IOException {

        Map<String, Set<String>> zebrafishAltIds = AlternateIdMapper.getAltIdMappingFile("drer", zebrafishTestFilename);
        assertEquals(zebrafishAltIds.get("ZDB-GENE-000128-8").toString(), "[ENSDARG00000086393]");
        assertEquals(zebrafishAltIds.size(), 5);
    }

    @Test
    public void mapYeastAlternateIdsTest() throws IOException {

        Map<String, Set<String>> yeastAltIds = AlternateIdMapper.getAltIdMappingFile("scer", yeastTestFilename);
        assertEquals(yeastAltIds.get("S000000002").toString(), "[YAL002W]");
        assertEquals(yeastAltIds.size(), 5);
    }
}