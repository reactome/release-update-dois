package org.reactome.release.orthopairs;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;


@RunWith(PowerMockRunner.class)
@PrepareForTest({OrthologyFileParser.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})
public class OrthologyFileParserTest {

    private String pantherTestFile = "src/test/resources/PantherTestFile.txt";
    private List<String> mockList = new ArrayList<>(Arrays.asList(pantherTestFile));
    private Object mockObject;
    private Object mockObject2;
    private Set<Object> mockObjectSet = new HashSet<>();
    private Set<String> mockStringSet = new HashSet<String>();

    @Mock
    private JSONObject mockJSONObject;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void parseJSONObjectsTest() throws IOException {
        mockObject = "key";
        mockObjectSet.add("key");
        mockJSONObject.put("key", "value");
        Mockito.when(mockJSONObject.keySet()).thenReturn(mockObjectSet);
        Mockito.when(mockJSONObject.get(mockObject)).thenReturn(mockJSONObject);
        Mockito.when(mockJSONObject.get("panther_name")).thenReturn(mockObject);
        OrthologyFileParser.parsePantherOrthologFiles(mockList, "sourceMappingSpecies", mockJSONObject);

    }

//    @Test
//    public void orthologyParserTest() throws Exception {
//        mockObject = "hsap";
//
////        mockObjectSet.add(mockObject);
//
//        mockJSONObject.put("HUMAN", "value");
////        Mockito.when(mockJSONObject.keySet()).thenReturn(mockObjectSet);
//
//        Mockito.when(mockJSONObject.get(mockObject)).thenReturn(mockJSONObject);
//        Mockito.when(mockJSONObject.get("panther_name")).thenReturn("HUMAN");
////        Mockito.when(mockStringSet.contains("DICDI")).thenReturn(true);
//
//        OrthologyFileParser.parsePantherOrthologFiles(mockList, "hsap", mockJSONObject);
//    }
}