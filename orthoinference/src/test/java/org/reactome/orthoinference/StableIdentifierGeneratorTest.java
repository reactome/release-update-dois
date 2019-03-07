package org.reactome.orthoinference;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({StableIdentifierGenerator.class, InstanceUtilities.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})
public class StableIdentifierGeneratorTest {

    private static Object identifier = "R-HSA-123456";
    private static String fakeAbbreviation = "ABC";
    @Mock
    Map<String,Integer> fakeSeenOrthoIds = new HashMap<>();

    @Mock
    MySQLAdaptor mockAdaptor;

    @Mock
    GKInstance mockInferredInst;

    @Mock
    GKInstance mockOriginalInst;

    @Mock
    GKInstance mockStableIdentifierInst;

    @Mock
    GKInstance mockOrthoStableIdentifierInst;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void generateOrthologousStableIdTest() throws Exception {

        PowerMockito.mockStatic(InstanceUtilities.class);
        Mockito.when(mockOriginalInst.getAttributeValue("stableIdentifier")).thenReturn(mockStableIdentifierInst);
        Mockito.when(mockStableIdentifierInst.getAttributeValue("identifier")).thenReturn(identifier);

        StableIdentifierGenerator.setSpeciesAbbreviation(fakeAbbreviation);
        StableIdentifierGenerator.setAdaptor(mockAdaptor);

        PowerMockito.when(InstanceUtilities.createNewInferredGKInstance(mockStableIdentifierInst)).thenReturn(mockOrthoStableIdentifierInst);

        StableIdentifierGenerator.generateOrthologousStableId(mockInferredInst, mockOriginalInst);

    }
}