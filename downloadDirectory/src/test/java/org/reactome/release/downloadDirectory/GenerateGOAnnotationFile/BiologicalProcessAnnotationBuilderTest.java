package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({BiologicalProcessAnnotationBuilder.class, GOAGeneratorUtilities.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})

public class BiologicalProcessAnnotationBuilderTest {

    @Mock
    private GKInstance mockReactionInst;
    @Mock
    private GKInstance mockCatalystInst;
    @Mock
    private GKInstance mockCatalystPEInst;
    @Mock
    private GKInstance mockCatalystPEMemberInst;
    @Mock
    private GKInstance mockReferenceEntityInst;
    @Mock
    private GKInstance mockSpeciesInst;
    @Mock
    private GKInstance mockCrossReferenceInst;
    @Mock
    private GKInstance mockEventReferralInst;
    @Mock
    private GKInstance mockGOBioProcessInst;
    @Mock
    private GKInstance mockStableIdentifierInst;
    @Mock
    private GKInstance mockProteinInst;

    @Mock
    private SchemaClass mockPESchemaClass;
    @Mock
    private SchemaClass mockMemberSchemaClass;

    private List<GKInstance> mockCatalystSet = new ArrayList<>();
    private List<GKInstance> mockMemberSet = new ArrayList<>();
    private List<GKInstance> mockEventReferralSet = new ArrayList<>();
    private List<GKInstance> mockGOBioProcessSet = new ArrayList<>();

    private Set<GKInstance> mockProteinSet = new HashSet<>();

    @Test
    public void biologicalProcessAnnotationBuilderWithCatalystTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);

        mockCatalystSet.add(mockCatalystInst);
        mockMemberSet.add(mockCatalystPEMemberInst);
        mockEventReferralSet.add(mockEventReferralInst);
        mockGOBioProcessSet.add(mockGOBioProcessInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)).thenReturn(mockCatalystSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity)).thenReturn(mockCatalystPEInst);
        Mockito.when(GOAGeneratorUtilities.validateCatalystPE(mockCatalystPEInst)).thenReturn(true);
        Mockito.when(mockCatalystPEInst.getSchemClass()).thenReturn(mockPESchemaClass);
        Mockito.when(GOAGeneratorUtilities.multiInstancePhysicalEntity(mockPESchemaClass)).thenReturn(true);
        Mockito.when(mockPESchemaClass.isa(ReactomeJavaConstants.EntitySet)).thenReturn(true);
        Mockito.when(mockCatalystPEInst.getAttributeValuesList(ReactomeJavaConstants.hasMember)).thenReturn(mockMemberSet);
        Mockito.when(mockCatalystPEMemberInst.getSchemClass()).thenReturn(mockMemberSchemaClass);
        Mockito.when(mockMemberSchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)).thenReturn(true);
        Mockito.when(mockCatalystPEMemberInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockCatalystPEMemberInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when((GOAGeneratorUtilities.validateProtein(mockReferenceEntityInst, mockSpeciesInst))).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockReactionInst.getReferers(ReactomeJavaConstants.hasEvent)).thenReturn(mockEventReferralSet);
        Mockito.when(mockEventReferralInst.getAttributeValuesList(ReactomeJavaConstants.goBiologicalProcess)).thenReturn(mockGOBioProcessSet);
        Mockito.when(mockGOBioProcessInst.getAttributeValue(ReactomeJavaConstants.accession)).thenReturn("123456");
        Mockito.when(mockEventReferralInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).thenReturn(mockStableIdentifierInst);
        Mockito.when(mockStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("A12345");
        BiologicalProcessAnnotationBuilder.processBiologicalFunctions(mockReactionInst);
    }

    @Test
    public void biologicalProcessAnnotationBuilderNoCatalystTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockProteinSet.add(mockProteinInst);
        mockGOBioProcessSet.add(mockGOBioProcessInst);
        Mockito.when(GOAGeneratorUtilities.retrieveProteins(mockReactionInst)).thenReturn(mockProteinSet);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockProteinInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when((GOAGeneratorUtilities.validateProtein(mockReferenceEntityInst, mockSpeciesInst))).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.goBiologicalProcess)).thenReturn(mockGOBioProcessSet);
        Mockito.when(mockGOBioProcessInst.getAttributeValue(ReactomeJavaConstants.accession)).thenReturn("123456");
        Mockito.when(mockReactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).thenReturn(mockStableIdentifierInst);
        Mockito.when(mockStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("A12345");
        BiologicalProcessAnnotationBuilder.processBiologicalFunctions(mockReactionInst);
    }

}
