package org.reactome.orthoinference;


import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.pathwaylayout.PredictedPathwayDiagramGeneratorFromDB;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OrthologousPathwayDiagramGenerator.class, PredictedPathwayDiagramGeneratorFromDB.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})
public class OrthologousPathwayDiagramGeneratorTest {

    @Mock
    MySQLAdaptor mockAdaptor;
    @Mock
    MySQLAdaptor mockPrevAdaptor;
    @Mock
    GKInstance mockSpeciesInst;
    @Mock
    GKInstance mockSpeciesInst2;
    @Mock
    GKInstance mockDiagramInst;
    @Mock
    GKInstance mockPathwayInst;
    @Mock
    GKInstance mockOrthoEventInst;
    @Mock
    GKInstance mockEvidenceTypeInst;
    @Mock
    GKInstance mockOrthoDiagramInst;
    @Mock
    PredictedPathwayDiagramGeneratorFromDB mockDiagramGenerator;

    long mockId = 12345L;

    Collection<GKInstance> mockDiagramInstances = new ArrayList<>();
    List<GKInstance> mockOrthoEventInstances = new ArrayList<>();


    OrthologousPathwayDiagramGenerator testDiagramGenerator;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        testDiagramGenerator = new OrthologousPathwayDiagramGenerator(mockAdaptor, mockPrevAdaptor, mockSpeciesInst, mockId, mockId);
    }
    @Test
    public void generateOrthologousPathwayDiagramsTest() throws Exception {

        mockDiagramInstances.add(mockDiagramInst);
        mockOrthoEventInstances.add(mockOrthoEventInst);

        PowerMockito.whenNew(PredictedPathwayDiagramGeneratorFromDB.class).withNoArguments().thenReturn(mockDiagramGenerator);
        Mockito.when(mockAdaptor.fetchInstance(mockId)).thenReturn(mockSpeciesInst);
        Mockito.when(mockSpeciesInst.getDisplayName()).thenReturn("Species");
        Mockito.when(mockAdaptor.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram)).thenReturn(mockDiagramInstances);
        Mockito.when(mockDiagramInst.getAttributeValue(ReactomeJavaConstants.representedPathway)).thenReturn(mockPathwayInst);
        Mockito.when(mockPathwayInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(mockPathwayInst.getAttributeValuesList(ReactomeJavaConstants.orthologousEvent)).thenReturn(mockOrthoEventInstances);
        Mockito.when(mockOrthoEventInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(mockOrthoEventInst.getAttributeValue(ReactomeJavaConstants.evidenceType)).thenReturn(mockEvidenceTypeInst);
        Mockito.when(mockDiagramGenerator.generatePredictedDiagram(mockOrthoEventInst, mockPathwayInst, mockDiagramInst)).thenReturn(mockOrthoDiagramInst);
        testDiagramGenerator.generateOrthologousPathwayDiagrams();
    }

    @Test
    public void sameSpeciesReturnsTrue() throws Exception {
        Mockito.when(mockPathwayInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        assertThat(testDiagramGenerator.isSameSpecies(mockPathwayInst, mockSpeciesInst), is(equalTo(true)));
    }

    @Test
    public void differentSpeciesReturnsFalse() throws Exception {
        Mockito.when(mockPathwayInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst2);
        assertThat(testDiagramGenerator.isSameSpecies(mockPathwayInst, mockSpeciesInst), is(equalTo(false)));
    }

    @Test
    public void electroniciallyInferredReturnsTrue() throws Exception {
        Mockito.when(mockPathwayInst.getAttributeValue(ReactomeJavaConstants.evidenceType)).thenReturn(mockEvidenceTypeInst);
        assertThat(testDiagramGenerator.isElectronicallyInferred(mockPathwayInst), is(equalTo(true)));
    }

    @Test
    public void manuallyInferredReturnsFalse() throws Exception {
        Mockito.when(mockPathwayInst.getAttributeValue(ReactomeJavaConstants.evidenceType)).thenReturn(null);
        assertThat(testDiagramGenerator.isElectronicallyInferred(mockPathwayInst), is(equalTo(false)));
    }

    @Test
    public void fewerDiagramCountsReturnsTrue() {
        assertThat(testDiagramGenerator.hasFewerSpeciesDiagramCountsBetweenReleases(4, 6), is(equalTo(true)));
    }

    @Test
    public void equalDiagramCountsReturnsFalse() {
        assertThat(testDiagramGenerator.hasFewerSpeciesDiagramCountsBetweenReleases(4, 4), is(equalTo(false)));
    }

    @Test
    public void moreDiagramCountsReturnsFalse() {
        assertThat(testDiagramGenerator.hasFewerSpeciesDiagramCountsBetweenReleases(6, 4), is(equalTo(false)));
    }

    @Test
    public void differentSpeciesDoesNotProducePathwayDiagram() throws Exception {
        Mockito.when(mockOrthoEventInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst2);
        Mockito.when(mockPathwayInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(mockOrthoEventInst.getAttributeValue(ReactomeJavaConstants.evidenceType)).thenReturn(mockEvidenceTypeInst);
        Mockito.when(mockDiagramGenerator.generatePredictedDiagram(mockOrthoEventInst, mockPathwayInst, mockDiagramInst)).thenReturn(mockOrthoDiagramInst);
        assertThat(testDiagramGenerator.generateOrthologousPathwayDiagram(mockOrthoEventInst, mockPathwayInst, mockDiagramInst, mockDiagramGenerator), is(nullValue()));
    }

    @Test
    public void manuallyInferredPathwayDoesNotProducePathwayDiagram() throws Exception {
        Mockito.when(mockOrthoEventInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(mockPathwayInst.getAttributeValue(ReactomeJavaConstants.evidenceType)).thenReturn(null);
        Mockito.when(mockDiagramGenerator.generatePredictedDiagram(mockOrthoEventInst, mockPathwayInst, mockDiagramInst)).thenReturn(mockOrthoDiagramInst);
        assertThat(testDiagramGenerator.generateOrthologousPathwayDiagram(mockOrthoEventInst, mockPathwayInst, mockDiagramInst, mockDiagramGenerator), is(nullValue()));
    }

    @Test
    public void sameSpeciesAndElectronicallyInferredProducesPathwayDiagram() throws Exception {
        Mockito.when(mockOrthoEventInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(mockOrthoEventInst.getAttributeValue(ReactomeJavaConstants.evidenceType)).thenReturn(mockEvidenceTypeInst);
        Mockito.when(mockDiagramGenerator.generatePredictedDiagram(mockOrthoEventInst, mockPathwayInst, mockDiagramInst)).thenReturn(mockOrthoDiagramInst);
        assertThat(testDiagramGenerator.generateOrthologousPathwayDiagram(mockOrthoEventInst, mockPathwayInst, mockDiagramInst, mockDiagramGenerator), is(equalTo(mockOrthoDiagramInst)));
    }
}
