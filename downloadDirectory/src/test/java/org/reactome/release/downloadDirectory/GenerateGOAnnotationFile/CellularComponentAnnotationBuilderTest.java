package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;


import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashSet;
import java.util.Set;

@RunWith(PowerMockRunner.class)
@PrepareForTest({CellularComponentAnnotationBuilder.class, GOAGeneratorUtilities.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})

public class CellularComponentAnnotationBuilderTest {

    @Mock
    private GKInstance mockReactionInst;
    @Mock
    private GKInstance mockProteinInst;
    @Mock
    private GKInstance mockReferenceEntityInst;
    @Mock
    private GKInstance mockSpeciesInst;
    @Mock
    private GKInstance mockCrossReferenceInst;
    @Mock
    private GKInstance mockStableIdentifierInst;
    @Mock
    private GKInstance mockCompartmentInst;

    private Set<GKInstance> mockProteinSet = new HashSet<>();

    @Test
    public void cellularComponentAnnotationBuilderTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);

        mockProteinSet.add(mockProteinInst);
        Mockito.when(GOAGeneratorUtilities.retrieveProteins(mockReactionInst)).thenReturn(mockProteinSet);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when((GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst))).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockReactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).thenReturn(mockStableIdentifierInst);
        Mockito.when(mockStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.compartment)).thenReturn(mockCompartmentInst);
        Mockito.when(mockCompartmentInst.getAttributeValue(ReactomeJavaConstants.accession)).thenReturn("1234");
        Mockito.when(GOAGeneratorUtilities.isProteinBindingAnnotation("1234")).thenReturn(false);
        CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);
    }

    @Test
    public void cellularComponentInvalidProteinTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);

        mockProteinSet.add(mockProteinInst);
        Mockito.when(GOAGeneratorUtilities.retrieveProteins(mockReactionInst)).thenReturn(mockProteinSet);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when((GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst))).thenReturn(false);
        CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);
    }

    @Test
    public void cellularComponentExcludedMicrobialSpeciesTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);

        mockProteinSet.add(mockProteinInst);
        Mockito.when(GOAGeneratorUtilities.retrieveProteins(mockReactionInst)).thenReturn(mockProteinSet);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when((GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst))).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("562");
        Mockito.when(GOAGeneratorUtilities.isExcludedMicrobialSpecies("562")).thenReturn(true);
        CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);
    }

    @Test
    public void cellularComponentAlternateGOCompartmentTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);

        mockProteinSet.add(mockProteinInst);
        Mockito.when(GOAGeneratorUtilities.retrieveProteins(mockReactionInst)).thenReturn(mockProteinSet);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when((GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst))).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("11676");
        CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);
    }

    @Test
    public void cellularComponentEmptyCompartmentTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);

        mockProteinSet.add(mockProteinInst);
        Mockito.when(GOAGeneratorUtilities.retrieveProteins(mockReactionInst)).thenReturn(mockProteinSet);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when((GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst))).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockReactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).thenReturn(mockStableIdentifierInst);
        Mockito.when(mockStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.compartment)).thenReturn(null);
        CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);
    }
}
