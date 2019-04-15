package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaClass;

import java.util.*;

public class MolecularFunctionAnnotationBuilder {

    private static final String MOLECULAR_FUNCTION_LETTER = "F";

    public static void processMolecularFunctions(GKInstance reactionInst) throws Exception {

        Collection<GKInstance> catalystInstances = reactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        for (GKInstance catalystInst : catalystInstances) {
                // Check that catalyst instance has no disqualifying attributes.
                boolean validCatalyst = validateMolecularFunctionCatalyst(catalystInst);
                if (validCatalyst) {
                    // For MF annotations, gets all proteins from the ActiveUnit if it exists or from the PhysicalEntity if not.
                    GKInstance activeUnitInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit);
                    GKInstance entityInst = activeUnitInst == null ? (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity) : activeUnitInst;
                    Set<GKInstance> proteinInstances = getMolecularFunctionProteins(entityInst);
                    processProteins(proteinInstances, reactionInst, catalystInst);
                }
        }
    }

    // This checks validity of a catalyst in a few ways. It first checks that the PhysicalEntity is valid.
    // Then it will check if the ActiveUnit is valid if it exists. If there is no ActiveUnit, the PhysicalEntity
    // is checked to see if it is 'multi-instance', meaning Complex, Polymer, or EntitySet. If so, it is considered invalid.
    // Catalysts with more than 1 ActiveUnit are also considered invalid.
    private static boolean validateMolecularFunctionCatalyst(GKInstance catalystInst) throws Exception {
        GKInstance catalystPEInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity);
        boolean validCatalystPE = GOAGeneratorUtilities.validateCatalystPE(catalystPEInst);
        boolean catalystValidity = false;
        if (validCatalystPE) {
            List<GKInstance> activeUnitInstances = catalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit);
            if (activeUnitInstances.size() == 0) {
                catalystValidity = GOAGeneratorUtilities.multiInstancePhysicalEntity(catalystPEInst.getSchemClass()) ? false : true;
            } else if (activeUnitInstances.size() == 1) {
                catalystValidity = validCatalystAU(activeUnitInstances.get(0));
            }
        }
        return catalystValidity;
    }

    // Validity of ActiveUnit is dependant on it not being an EntitySet with non-EWAS members.
    // This rule used to also exclude Complex and Polymers, but it seems ActiveUnits can't be either of those.
    private static boolean validCatalystAU(GKInstance activeUnitInst) throws Exception {
        SchemaClass activeUnitSchemaClass = activeUnitInst.getSchemClass();
        if (activeUnitSchemaClass.isa(ReactomeJavaConstants.EntitySet) && !onlyEWASMembers(activeUnitInst)) {
            return false;
        }
        if (activeUnitSchemaClass.isa(ReactomeJavaConstants.Complex) || activeUnitSchemaClass.isa(ReactomeJavaConstants.Polymer)) {
            return false;
        }
        return true;
    }

    // Retrieves all protein instances from an EntitySet comprised of only EWAS' or that are not a Complex/Polymer but are an EWAS.
    private static Set<GKInstance> getMolecularFunctionProteins(GKInstance entityInst) throws Exception {
        Set<GKInstance> proteinInstances = new HashSet<>();
        SchemaClass entitySchemaClass = entityInst.getSchemClass();
        if (entitySchemaClass.isa(ReactomeJavaConstants.EntitySet) && onlyEWASMembers(entityInst)) {
            proteinInstances.addAll(entityInst.getAttributeValuesList(ReactomeJavaConstants.hasMember));
        } else if (entitySchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
            proteinInstances.add(entityInst);
        }
        return proteinInstances;
    }

    // Checks that the incoming EntitySet instance only has EWAS members
    private static boolean onlyEWASMembers(GKInstance entitySetInst) throws Exception {
       Collection<GKInstance> memberInstances = (Collection<GKInstance>) entitySetInst.getAttributeValuesList(ReactomeJavaConstants.hasMember);
        return memberInstances
                .stream()
                .allMatch(member -> member.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence));

    }

    // Attempt to generate GO Annotation information for each protein associated with the whole Reaction or just it's catalysts, depending on the goTerm being evaluated.
    private static void processProteins(Set<GKInstance> proteinInstances, GKInstance reactionInst, GKInstance catalystInst) throws Exception {

        for (GKInstance proteinInst : proteinInstances) {
            GKInstance referenceEntityInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            GKInstance speciesInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.species);
            // Check if the protein has any disqualifying attributes.
            boolean validProtein = GOAGeneratorUtilities.validateProtein(referenceEntityInst, speciesInst);
            if (validProtein) {
                String taxonIdentifier = ((GKInstance) speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
                if (!GOAGeneratorUtilities.excludedMicrobialSpecies(taxonIdentifier) && catalystInst.getAttributeValue(ReactomeJavaConstants.activity) != null) {
                    getGOMolecularFunctionLine(catalystInst, referenceEntityInst, taxonIdentifier, reactionInst);
                }
            }
        }
    }

    // Generating GOA lines for MF annotations depends on if the catalyst has any literatureReferences (meaning it has a PubMed annotation). If it does, multiple GOA lines
    // that are specific to each PubMed annotation will be output, or, if there are no literatureReferences just a single line with a Reactome identifier will be output.
    // The GOA generation will be called differently depending on this.
    private static void getGOMolecularFunctionLine(GKInstance catalystInst, GKInstance referenceEntityInst, String taxonIdentifier, GKInstance reactionInst) throws Exception {
        List<String> pubMedIdentifiers = new ArrayList<>();
        for (GKInstance literatureReferenceInst : (Collection<GKInstance>) catalystInst.getAttributeValuesList(ReactomeJavaConstants.literatureReference)) {
            pubMedIdentifiers.add("PMID:" + literatureReferenceInst.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier).toString());
        }
        GKInstance activityInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.activity);
        if (!GOAGeneratorUtilities.accessionForProteinBindingAnnotation(activityInst.getAttributeValue(ReactomeJavaConstants.accession))) {
            String goAccession = "GO:" + activityInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
            if (pubMedIdentifiers.size() > 0) {
                for (String pubmedIdentifier : pubMedIdentifiers) {
                    String goaLine = GOAGeneratorUtilities.generateGOALine(referenceEntityInst, MOLECULAR_FUNCTION_LETTER, goAccession, pubmedIdentifier, "EXP", taxonIdentifier);
                    GOAGeneratorUtilities.assignDateForGOALine(catalystInst, goaLine);
                }
            } else {
                String reactomeIdentifier=  "REACTOME:" + ((GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
                String goaLine = GOAGeneratorUtilities.generateGOALine(referenceEntityInst, MOLECULAR_FUNCTION_LETTER, goAccession, reactomeIdentifier, "TAS", taxonIdentifier);
                GOAGeneratorUtilities.assignDateForGOALine(catalystInst, goaLine);
            }
        }
    }
}
