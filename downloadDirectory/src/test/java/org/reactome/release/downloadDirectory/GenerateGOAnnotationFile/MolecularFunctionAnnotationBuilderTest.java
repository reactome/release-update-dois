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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
    private GKInstance mockMemberInst;
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
    private GKInstance mockStableIdentifierInst;
    @Mock
    private SchemaClass mockSchemaClass;


    private List<GKInstance> mockCatalystSet = new ArrayList<>();
    private List<GKInstance> mockActiveUnitSet = new ArrayList<>();
    private List<GKInstance> mockLitRefSet = new ArrayList<>();
    private List<GKInstance> mockMemberSet = new ArrayList<>();

    @Test
    public void molecularFunctionAnnotationLineBuilderTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockCatalystSet.add(mockCatalystInst);
        mockActiveUnitSet.add(mockActiveUnitInst);
        mockLitRefSet.add(mockLitRefInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)).thenReturn(mockCatalystSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity)).thenReturn(mockCatalystPEInst);
        Mockito.when(GOAGeneratorUtilities.isValidCatalystPE(mockCatalystPEInst)).thenReturn(true);
        Mockito.when(mockCatalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitInst);
        Mockito.when(mockActiveUnitInst.getSchemClass()).thenReturn(mockSchemaClass);
        Mockito.when(mockSchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)).thenReturn(true);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst)).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activity)).thenReturn(mockGOMolecularFunctionInst);
        Mockito.when(mockCatalystInst.getAttributeValuesList(ReactomeJavaConstants.literatureReference)).thenReturn(mockLitRefSet);
        Mockito.when(mockLitRefInst.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier)).thenReturn("1234");
        Mockito.when(mockGOMolecularFunctionInst.getAttributeValue(ReactomeJavaConstants.accession)).thenReturn("1234");
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("R1234");
        Mockito.when(GOAGeneratorUtilities.getSecondaryIdentifier(mockReferenceEntityInst)).thenReturn("R5678");
        Mockito.when(GOAGeneratorUtilities.getStableIdentifierIdentifier(mockReactionInst)).thenReturn("1234");
        Mockito.when(GOAGeneratorUtilities.generateGOALine(mockReferenceEntityInst, GOAGeneratorConstants.MOLECULAR_FUNCTION_LETTER, "GO:1234", "PMID:1234", GOAGeneratorConstants.INFERRED_FROM_EXPERIMENT_CODE, "1234")).thenCallRealMethod();
        List<String> goaLines = MolecularFunctionAnnotationBuilder.processMolecularFunctions(mockReactionInst);
        assertThat(goaLines.size(), is(equalTo(1)));
        assertThat(goaLines.get(0), is((equalTo("UniProtKB\tR1234\tR5678\t\tGO:1234\tPMID:1234\tEXP\t\tF\t\t\tprotein\ttaxon:1234"))));
    }

    @Test
    public void molecularFunctionNoPubMedIdentifierLineBuilderTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockCatalystSet.add(mockCatalystInst);
        mockActiveUnitSet.add(mockActiveUnitInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)).thenReturn(mockCatalystSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity)).thenReturn(mockCatalystPEInst);
        Mockito.when(GOAGeneratorUtilities.isValidCatalystPE(mockCatalystPEInst)).thenReturn(true);
        Mockito.when(mockCatalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitInst);
        Mockito.when(mockActiveUnitInst.getSchemClass()).thenReturn(mockSchemaClass);
        Mockito.when(mockSchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)).thenReturn(true);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst)).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activity)).thenReturn(mockGOMolecularFunctionInst);
        Mockito.when(mockGOMolecularFunctionInst.getAttributeValue(ReactomeJavaConstants.accession)).thenReturn("1234");
        Mockito.when(mockReactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).thenReturn(mockStableIdentifierInst);
        Mockito.when(mockStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockReferenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("R1234");
        Mockito.when(GOAGeneratorUtilities.getSecondaryIdentifier(mockReferenceEntityInst)).thenReturn("R5678");
        Mockito.when(GOAGeneratorUtilities.getStableIdentifierIdentifier(mockReactionInst)).thenReturn("1234");
        Mockito.when(GOAGeneratorUtilities.generateGOALine(mockReferenceEntityInst, GOAGeneratorConstants.MOLECULAR_FUNCTION_LETTER, "GO:1234", "REACTOME:1234", GOAGeneratorConstants.TRACEABLE_AUTHOR_STATEMENT_CODE, "1234")).thenCallRealMethod();
        List<String> goaLines = MolecularFunctionAnnotationBuilder.processMolecularFunctions(mockReactionInst);
        assertThat(goaLines.size(), is(equalTo(1)));
        assertThat(goaLines.get(0), is((equalTo("UniProtKB\tR1234\tR5678\t\tGO:1234\tREACTOME:1234\tTAS\t\tF\t\t\tprotein\ttaxon:1234"))));
    }

    @Test
    public void molecularFunctionInvalidCatalystReturnsZeroLinesTest() throws Exception {
        mockCatalystSet.add(mockCatalystInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)).thenReturn(mockCatalystSet);
        Mockito.when(GOAGeneratorUtilities.isValidCatalystPE(mockCatalystPEInst)).thenReturn(false);
        List<String> goaLines = MolecularFunctionAnnotationBuilder.processMolecularFunctions(mockReactionInst);
        assertThat(goaLines.size(), is(equalTo(0)));
    }

    @Test
    public void molecularFunctionNotOnlyEWASMembersReturnsZeroLinesTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockCatalystSet.add(mockCatalystInst);
        mockActiveUnitSet.add(mockActiveUnitInst);
        mockMemberSet.add(mockMemberInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)).thenReturn(mockCatalystSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity)).thenReturn(mockCatalystPEInst);
        Mockito.when(GOAGeneratorUtilities.isValidCatalystPE(mockCatalystPEInst)).thenReturn(true);
        Mockito.when(mockCatalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitInst);
        Mockito.when(mockActiveUnitInst.getSchemClass()).thenReturn(mockSchemaClass);
        Mockito.when(mockSchemaClass.isa(ReactomeJavaConstants.EntitySet)).thenReturn(true);
        Mockito.when(mockActiveUnitInst.getAttributeValuesList(ReactomeJavaConstants.hasMember)).thenReturn(mockMemberSet);
        Mockito.when(mockMemberInst.getSchemClass()).thenReturn(mockSchemaClass);
        List<String> goaLines = MolecularFunctionAnnotationBuilder.processMolecularFunctions(mockReactionInst);
        assertThat(goaLines.size(), is(equalTo(0)));
    }

    @Test
    public void molecularFunctionNullCatalystActivityReturnsZeroLinesTest() throws Exception {
        PowerMockito.mockStatic(GOAGeneratorUtilities.class);
        mockCatalystSet.add(mockCatalystInst);
        mockActiveUnitSet.add(mockActiveUnitInst);
        mockLitRefSet.add(mockLitRefInst);

        Mockito.when(mockReactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity)).thenReturn(mockCatalystSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity)).thenReturn(mockCatalystPEInst);
        Mockito.when(GOAGeneratorUtilities.isValidCatalystPE(mockCatalystPEInst)).thenReturn(true);
        Mockito.when(mockCatalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitSet);
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit)).thenReturn(mockActiveUnitInst);
        Mockito.when(mockActiveUnitInst.getSchemClass()).thenReturn(mockSchemaClass);
        Mockito.when(mockSchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)).thenReturn(true);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.referenceEntity)).thenReturn(mockReferenceEntityInst);
        Mockito.when(mockActiveUnitInst.getAttributeValue(ReactomeJavaConstants.species)).thenReturn(mockSpeciesInst);
        Mockito.when(GOAGeneratorUtilities.isValidProtein(mockReferenceEntityInst, mockSpeciesInst)).thenReturn(true);
        Mockito.when(((GKInstance) mockSpeciesInst.getAttributeValue(ReactomeJavaConstants.crossReference))).thenReturn(mockCrossReferenceInst);
        Mockito.when(mockCrossReferenceInst.getAttributeValue(ReactomeJavaConstants.identifier)).thenReturn("1234");
        Mockito.when(mockCatalystInst.getAttributeValue(ReactomeJavaConstants.activity)).thenReturn(null);
        List<String> goaLines = MolecularFunctionAnnotationBuilder.processMolecularFunctions(mockReactionInst);
        assertThat(goaLines.size(), is(equalTo(0)));
    }
}
