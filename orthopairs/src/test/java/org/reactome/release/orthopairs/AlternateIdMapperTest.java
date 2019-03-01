package org.reactome.release.orthopairs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;

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

        AlternateIdMapper.getAltIdMappingFile("mmus", mouseTestFilename);
    }

    @Test
    public void mapRatAlternateIdsTest() throws IOException {

        AlternateIdMapper.getAltIdMappingFile("rnor", ratTestFilename);
    }

    @Test
    public void mapFrogAlternateIdsTest() throws IOException {

        AlternateIdMapper.getAltIdMappingFile("xtro", frogTestFilename);
    }

    @Test
    public void mapZebrafishAlternateIdsTest() throws IOException {

        AlternateIdMapper.getAltIdMappingFile("drer", zebrafishTestFilename);
    }

    @Test
    public void mapYeastAlternateIdsTest() throws IOException {

        AlternateIdMapper.getAltIdMappingFile("scer", yeastTestFilename);
    }


}