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
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
    public void cellularComponentAnnotationLineBuilderTest() throws Exception {
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
        Mockito.when(GOAGeneratorUtilities.getStableIdentifierIdentifier(mockReactionInst)).thenReturn("1234");
        Mockito.when(GOAGeneratorUtilities.isProteinBindingAnnotation(mockCompartmentInst)).thenReturn(false);
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("R1234");
        Mockito.when(GOAGeneratorUtilities.getSecondaryIdentifier(mockReferenceEntityInst)).thenReturn("R5678");
        Mockito.when(GOAGeneratorUtilities.generateGOALine(mockReferenceEntityInst, GOAGeneratorConstants.CELLULAR_COMPONENT_LETTER, "GO:1234", "REACTOME:1234", GOAGeneratorConstants.TRACEABLE_AUTHOR_STATEMENT_CODE, "1234")).thenCallRealMethod();
        List<String> goaLines = CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(1)));
        assertThat(goaLines.get(0), is((equalTo("UniProtKB\tR1234\tR5678\t\tGO:1234\tREACTOME:1234\tTAS\t\tC\t\t\tprotein\ttaxon:1234"))));
    }

    @Test
    public void cellularComponentAlternateGOCompartmentReturnsZeroLinesTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockProteinSet.add(mockProteinInst);

        Mockito.when(GOAGeneratorUtilities.retrieveProteins(mockReactionInst)).thenReturn(mockProteinSet);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when((GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst))).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("11676");
        List<String> goaLines = CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(0)));

    }

    @Test
    public void cellularComponentEmptyCompartmentReturnsArrayWithEmptyStringTest() throws Exception {
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
        List<String> goaLines = CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(1)));
        assertThat(goaLines.get(0), is(equalTo("")));
    }

    @Test
    public void cellularComponentExcludedMicrobialSpeciesReturnsArrayWithEmptyStringTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockProteinSet.add(mockProteinInst);

        Mockito.when(GOAGeneratorUtilities.retrieveProteins(mockReactionInst)).thenReturn(mockProteinSet);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when((GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst))).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("813");
        Mockito.when(GOAGeneratorUtilities.isExcludedMicrobialSpecies("813")).thenCallRealMethod();
        List<String> goaLines = CellularComponentAnnotationBuilder.processCellularComponents(mockReactionInst);

        assertThat(goaLines.size(), is(equalTo(0)));
    }
}
