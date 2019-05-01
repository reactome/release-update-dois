package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GOAGeneratorUtilities.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})


public class GOAGeneratorUtilitiesTest {

    @Mock
    private GKInstance mockReferenceEntityInst;
    @Mock
    private GKInstance mockSpeciesInst;
    @Mock
    private GKInstance mockRefDatabaseInst;
    @Mock
    private GKInstance mockCrossReferenceInst;
    @Mock
    private GKInstance mockCatatlystPEInst;
    @Mock
    private GKInstance mockCompartmentInst;
    @Mock
    private GKInstance mockReactionInst;
    @Mock
    private GKInstance mockModifiedInst;

    @Mock
    private SchemaClass mockSchemaClass;

    private List<GKInstance> mockModifiedSet = new ArrayList<>();

    private final String testGOALine = "UniProtKB\tABCD1234\tABCD1\t\tA12345\tREACTOME:123456\tTAS\t\tC\t\t\tprotein\ttaxon:54321A";


    @Test
    public void validateProteinTest() throws Exception {
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase)).thenReturn(mockRefDatabaseInst);
        Mockito.when(mockRefDatabaseInst.getDisplayName()).thenReturn("UniProt");
        Mockito.when(mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).thenReturn(mockCrossReferenceInst);
        assertTrue(GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst));

        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase)).thenReturn(mockRefDatabaseInst);
        Mockito.when(mockRefDatabaseInst.getDisplayName()).thenReturn("UniProt");
        Mockito.when(mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).thenReturn(null);
        assertFalse(GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst));
    }

    @Test
    public void validateCatalystPETest() throws Exception {
        Mockito.when(mockCatatlystPEInst.getAttributeValue(ReactomeJavaConstants.compartment)).thenReturn(mockCompartmentInst);
        assertTrue(GOAGeneratorUtilities.isValidCatalystPE(mockCatatlystPEInst));
    }

    @Test
    public void multiInstancePhysicalEntityTest() {
        Mockito.when(mockSchemaClass.isa(ReactomeJavaConstants.EntitySet)).thenReturn(true);
        assertTrue(GOAGeneratorUtilities.isMultiInstancePhysicalEntity(mockSchemaClass));
        Mockito.when(mockSchemaClass.isa(ReactomeJavaConstants.EntitySet)).thenReturn(false);
        assertFalse(GOAGeneratorUtilities.isMultiInstancePhysicalEntity(mockSchemaClass));
    }

    @Test
    public void generateGOALineTest() throws Exception {
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("ABCD1234");
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName)).thenReturn("ABCD1");
        String goaLine = GOAGeneratorUtilities.generateGOALine(mockReferenceEntityInst, "C", "A12345", "REACTOME:123456", "TAS", "54321A");
        assertEquals(testGOALine, goaLine);
    }

    @Test
    public void excludedMicrobialSpeciesTest() {
        assertTrue(GOAGeneratorUtilities.isExcludedMicrobialSpecies("813"));
        assertFalse(GOAGeneratorUtilities.isExcludedMicrobialSpecies("812"));
    }

    @Test
    public void proteinBindingAnnotationTest() {
        assertTrue(GOAGeneratorUtilities.isProteinBindingAnnotation("0005515"));
        assertFalse(GOAGeneratorUtilities.isProteinBindingAnnotation("1234567"));
    }

    @Test
    public void assignDateForGOALineTest() throws Exception {

        mockModifiedSet.add(mockModifiedInst);
        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.modified)).thenReturn(mockModifiedSet);
        Mockito.when(mockModifiedInst.getAttributeValue(ReactomeJavaConstants.dateTime)).thenReturn("2019-01-01 01:01:01.0");
        int testDate = GOAGeneratorUtilities.assignDateForGOALine(mockReactionInst, testGOALine);
        assertEquals(20190101, testDate);
    }
}
