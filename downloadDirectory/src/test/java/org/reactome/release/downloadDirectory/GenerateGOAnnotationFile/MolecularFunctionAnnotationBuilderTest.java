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
@PrepareForTest({MolecularFunctionAnnotationBuilder.class, GOAGeneratorUtilities.class})
@PowerMockIgnore({"org.apache.logging.log4j.*", "javax.management.*", "javax.script.*",
        "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.sax.*", "com.sun.xml.*", "org.w3c.dom.*", "org.mockito.*"})

public class MolecularFunctionAnnotationBuilderTest {

    @Mock
    private GKInstance mockReactionInst;
    @Mock
    private GKInstance mockCatalystInst;
    @Mock
    private GKInstance mockCatalystPEInst;
    @Mock
    private GKInstance mockActiveUnitInst;
    @Mock
    private GKInstance mockReferenceEntityInst;
    @Mock
    private GKInstance mockSpeciesInst;
    @Mock
    private GKInstance mockCrossReferenceInst;
    @Mock
    private GKInstance mockGOMolecularFunctionInst;
    @Mock
    private GKInstance mockLitRefInst;
    @Mock
    private SchemaClass mockSchemaClass;


    private List<GKInstance> mockCatalystSet = new ArrayList<>();
    private List<GKInstance> mockActiveUnitSet = new ArrayList<>();
    private List<GKInstance> mockLitRefSet = new ArrayList<>();

    @Test
    public void molecularFunctionAnnotationBuilderTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockCatalystSet.add(mockCatalystInst);
        mockActiveUnitSet.add(mockActiveUnitInst);
        mockLitRefSet.add(mockLitRefInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)).thenReturn(mockCatalystSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity)).thenReturn(mockCatalystPEInst);
        Mockito.when(GOAGeneratorUtilities.validateCatalystPE(mockCatalystPEInst)).thenReturn(true);
        Mockito.when(mockCatalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitInst);
        Mockito.when(mockActiveUnitInst.getSchemClass()).thenReturn(mockSchemaClass);
        Mockito.when(mockSchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)).thenReturn(true);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(GOAGeneratorUtilities.validateProtein(mockReferenceEntityInst, mockSpeciesInst)).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activity)).thenReturn(mockGOMolecularFunctionInst);
        Mockito.when(mockCatalystInst.getAttributeValuesList(ReactomeJavaConstants.literatureReference)).thenReturn(mockLitRefSet);
        Mockito.when(mockLitRefInst.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier)).thenReturn("123456");
        Mockito.when(mockGOMolecularFunctionInst.getAttributeValue(ReactomeJavaConstants.accession)).thenReturn("123456");
        MolecularFunctionAnnotationBuilder.processMolecularFunctions(mockReactionInst);
    }

}
