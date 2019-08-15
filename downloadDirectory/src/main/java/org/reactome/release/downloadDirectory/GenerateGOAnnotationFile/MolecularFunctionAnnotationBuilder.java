package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaClass;

import static org.reactome.release.downloadDirectory.GenerateGOAnnotationFile.GOAGeneratorConstants.*;

import java.util.*;

public class MolecularFunctionAnnotationBuilder {

    private static final Logger logger = LogManager.getLogger();

    /**
     * Initial Molecular Function annotations method that iterates through and validates the reaction's catalyst instances, if any exist.
     * @param reactionInst -- GKInstance from ReactionlikeEvent class.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static List<String> processMolecularFunctions(GKInstance reactionInst) throws Exception {
        List<String> goaLines = new ArrayList<>();
        Collection<GKInstance> catalystInstances = reactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        for (GKInstance catalystInst : catalystInstances) {
            // Check that catalyst instance has no disqualifying attributes.
            boolean validCatalyst = isValidMolecularFunctionCatalyst(catalystInst);
            if (validCatalyst) {
                // For MF annotations, gets all proteins from the ActiveUnit if it exists or from the PhysicalEntity if not.
                GKInstance activeUnitInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit);
                GKInstance entityInst = activeUnitInst == null ? (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity) : activeUnitInst;
                Set<GKInstance> proteinInstances = getMolecularFunctionProteins(entityInst);
                goaLines.addAll(processProteins(proteinInstances, reactionInst, catalystInst));
            } else {
                logger.info("Invalid catalyst, skipping GO annotation");
            }
        }
        return goaLines;
    }

    /**
     * This checks validity of a catalyst in a few ways. It first checks that the Catalyst PhysicalEntity is valid.
     * Then it will check if the ActiveUnit is valid if it exists. If there is no ActiveUnit, the PhysicalEntity
     * is checked to see if it is 'multi-instance', which will invalidate it.
     * Catalysts with more than 1 ActiveUnit are also considered invalid.
     * @param catalystInst -- GKInstance, catalyst instance taken from the original reaction instance.
     * @return -- boolean, True if the catalyst instance has been deemed valid, false if not.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static boolean isValidMolecularFunctionCatalyst(GKInstance catalystInst) throws Exception {
        GKInstance catalystPEInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity);
        boolean validCatalystPE = GOAGeneratorUtilities.isValidCatalystPE(catalystPEInst);
        boolean catalystValidity = false;
        if (validCatalystPE) {
            List<GKInstance> activeUnitInstances = catalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit);
            if (activeUnitInstances.size() == 0) {
                catalystValidity = GOAGeneratorUtilities.isMultiInstancePhysicalEntity(catalystPEInst.getSchemClass()) ? false : true;
            } else if (activeUnitInstances.size() == 1) {
                catalystValidity = isValidCatalystAU(activeUnitInstances.get(0));
            }
        }
        if (!catalystValidity) {
            logger.info("Invalid catalyst, skipping GO annotation");
        }
        return catalystValidity;
    }

    /**
     *  Validity of ActiveUnit is dependant on it not being an EntitySet with non-EWAS members.
     *  This rule used to also exclude Complex and Polymers, but it seems ActiveUnits can't be either of those.
     * @param activeUnitInst -- GKInstance, active unit instance taken from the catalyst instance.
     * @return -- boolean, True if the activeUnit instance has been deemed valid, false if not.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static boolean isValidCatalystAU(GKInstance activeUnitInst) throws Exception {
        SchemaClass activeUnitSchemaClass = activeUnitInst.getSchemClass();
        if (activeUnitSchemaClass.isa(ReactomeJavaConstants.EntitySet) && isOnlyEWASMembers(activeUnitInst) || activeUnitSchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
            return true;
        }
        return false;
    }

    /**
     * Retrieves all protein instances from an EntitySet comprised of only EWAS' or that are not a Complex/Polymer but are an EWAS.
     * @param entityInst -- GKInstance, ActiveUnit/PhysicalActivity instance taken from a Reaction's catalyst.
     * @return -- Set of GKInstances, retrieved protein instances from either ActiveUnit or PhysicalEntity.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static Set<GKInstance> getMolecularFunctionProteins(GKInstance entityInst) throws Exception {
        Set<GKInstance> proteinInstances = new HashSet<>();
        SchemaClass entitySchemaClass = entityInst.getSchemClass();
        if (entitySchemaClass.isa(ReactomeJavaConstants.EntitySet) && isOnlyEWASMembers(entityInst)) {
            proteinInstances.addAll(entityInst.getAttributeValuesList(ReactomeJavaConstants.hasMember));
        } else if (entitySchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
            proteinInstances.add(entityInst);
        }
        return proteinInstances;
    }

    /**
     * Checks that the incoming EntitySet instance only has EWAS members
     * @param entitySetInst -- GKInstance,  ActiveUnit/PhysicalEntity of a catalyst that is an EntitySet
     * @return -- boolean, true if the instance only contains EWAS' in its hasMember attribute, false if not.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static boolean isOnlyEWASMembers(GKInstance entitySetInst) throws Exception {
       Collection<GKInstance> memberInstances = (Collection<GKInstance>) entitySetInst.getAttributeValuesList(ReactomeJavaConstants.hasMember);
        return memberInstances
                .stream()
                .allMatch(member -> member.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence));

    }

    /**
     * Iterates through each protein from a Catalyst's ActiveUnit/PhysicalEntity, filtering out any that are invalid, are from the excluded species or that have no activity value.
     * @param proteinInstances -- Set of GKInstances, EWAS' from the ActiveUnit/PhysicalEntity of the catalyst.
     * @param reactionInst -- GKInstance, parent reaction the catalyst/proteins come from.
     * @param catalystInst -- GKInstance, catalyst instance from reaction.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static List<String> processProteins(Set<GKInstance> proteinInstances, GKInstance reactionInst, GKInstance catalystInst) throws Exception {
        List<String> goaLines = new ArrayList<>();
        for (GKInstance proteinInst : proteinInstances) {
            GKInstance referenceEntityInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            GKInstance speciesInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.species);
            // Check if the protein has any disqualifying attributes.
            boolean validProtein = GOAGeneratorUtilities.isValidProtein(referenceEntityInst, speciesInst);
            if (validProtein) {
                String taxonIdentifier = ((GKInstance) speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
                if (!GOAGeneratorUtilities.isExcludedMicrobialSpecies(taxonIdentifier)) {
                    if (catalystInst.getAttributeValue(ReactomeJavaConstants.activity) != null) {
                        goaLines.addAll(generateGOMolecularFunctionLine(catalystInst, referenceEntityInst, taxonIdentifier, reactionInst));
                    } else {
                        logger.info("Catalyst has no GO_MolecularFunction attribute, skipping GO annotation");
                    }
                } else {
                    logger.info("Protein is from an excluded microbial species, skipping GO annotation");
                }
            } else {
                logger.info("Invalid protein, skipping GO annotation");
            }
        }
        return goaLines;
    }

    /**
     * Generating GOA lines for MF annotations depends on if the catalyst has any literatureReferences (meaning it has a PubMed annotation). If it does, multiple GOA lines
     * that are specific to each PubMed annotation will be output, or, if there are no literatureReferences just a single line with a Reactome identifier will be output.
     * The GOA line generation will be called differently depending on this.
     * @param catalystInst -- GKInstance, catalyst instance from reaction.
     * @param referenceEntityInst -- GKInstance, ReferenceEntity instance from protein instance.
     * @param taxonIdentifier -- String, CrossReference ID of protein's species.
     * @param reactionInst -- GKInstance, parent reaction instance that protein/catalyst comes from.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static List<String> generateGOMolecularFunctionLine(GKInstance catalystInst, GKInstance referenceEntityInst, String taxonIdentifier, GKInstance reactionInst) throws Exception {
        List<String> goaLines = new ArrayList<>();
        List<String> pubMedIdentifiers = new ArrayList<>();
        for (GKInstance literatureReferenceInst : (Collection<GKInstance>) catalystInst.getAttributeValuesList(ReactomeJavaConstants.literatureReference)) {
            pubMedIdentifiers.add(PUBMED_IDENTIFIER_PREFIX + literatureReferenceInst.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier).toString());
        }
        GKInstance activityInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.activity);
        String goaLine = "";
        if (!GOAGeneratorUtilities.isProteinBindingAnnotation(activityInst)) {
            String goAccession = GO_IDENTIFIER_PREFIX + activityInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
            if (pubMedIdentifiers.size() > 0) {
                for (String pubmedIdentifier : pubMedIdentifiers) {
                    goaLine = GOAGeneratorUtilities.generateGOALine(referenceEntityInst, MOLECULAR_FUNCTION_LETTER, goAccession, pubmedIdentifier, INFERRED_FROM_EXPERIMENT_CODE, taxonIdentifier);
                    GOAGeneratorUtilities.assignDateForGOALine(catalystInst, goaLine);
                    goaLines.add(goaLine);
                }
            } else {
                String reactomeIdentifier = REACTOME_IDENTIFIER_PREFIX + GOAGeneratorUtilities.getStableIdentifierIdentifier(reactionInst);
                goaLine = GOAGeneratorUtilities.generateGOALine(referenceEntityInst, MOLECULAR_FUNCTION_LETTER, goAccession, reactomeIdentifier, TRACEABLE_AUTHOR_STATEMENT_CODE, taxonIdentifier);
                GOAGeneratorUtilities.assignDateForGOALine(catalystInst, goaLine);
                goaLines.add(goaLine);
            }
        } else {
            logger.info("Accession is for protein binding, skipping GO annotation");
        }
        return goaLines;
    }
}
