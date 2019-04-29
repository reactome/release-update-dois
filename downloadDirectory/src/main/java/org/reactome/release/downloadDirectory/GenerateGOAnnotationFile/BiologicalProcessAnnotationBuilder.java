package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaClass;

import java.util.*;

public class BiologicalProcessAnnotationBuilder {

    private static final Logger logger = LogManager.getLogger();

    // When attempting to find BiologicalProcess accessions, sometimes referral Event instances need to be checked. We cap this at 2 recursions (the parent and grandparent referrals).
    private static final int MAX_RECURSION_LEVEL = 2;

    /**
     * Initial Biological Function annotations method that determines how to retrieve proteins for annotation. Protein retrieval methods differ depending on the presence of a catalyst.
     * @param reactionInst
     * @throws Exception
     */
    public static void processBiologicalFunctions(GKInstance reactionInst) throws Exception {

        Collection<GKInstance> catalystInstances = reactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        if (catalystInstances.size() == 0) {
            Set<GKInstance> proteinInstances = GOAGeneratorUtilities.retrieveProteins(reactionInst);
            processProteins(proteinInstances, reactionInst);
        } else {
            // Check that catalyst instance has no disqualifying attributes.
            for (GKInstance catalystInst : catalystInstances) {
                GKInstance catalystPEInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity);
                boolean validCatalyst = GOAGeneratorUtilities.validateCatalystPE(catalystPEInst);
                if (validCatalyst) {
                    Set<GKInstance> proteinInstances = getBiologicalProcessProteins(catalystPEInst);
                    processProteins(proteinInstances, reactionInst);
                } else {
                    logger.info("Invalid catalyst, skipping GO annotation");
                }
            }
        }
    }

    /**
     * Returns all protein instances from multi-instance PhysicalEntitys (Polymer/Complex/EntitySet) or just the single incoming instance if it is an EWAS.
     * @param physicalEntityInst -- PhysicalEntity instance from catalyst.
     * @return
     * @throws Exception
     */
    private static Set<GKInstance> getBiologicalProcessProteins(GKInstance physicalEntityInst) throws Exception {
        Set<GKInstance> proteinInstances = new HashSet<>();
        if (GOAGeneratorUtilities.multiInstancePhysicalEntity(physicalEntityInst.getSchemClass())) {
            proteinInstances.addAll(getMultiInstanceSubInstances(physicalEntityInst));
        } else if (physicalEntityInst.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
            proteinInstances.add(physicalEntityInst);
        }
        return proteinInstances;
    }

    /**
     * Returns all constituent PhysicalEntity instances of a Multi-instance PhysicalEntity
     * @param physicalEntityInst
     * @return
     * @throws Exception
     */
    private static Set<GKInstance> getMultiInstanceSubInstances(GKInstance physicalEntityInst) throws Exception {
        Set<GKInstance> subInstanceProteins = new HashSet<>();
        for (GKInstance subunitInst : (Collection<GKInstance>)  physicalEntityInst.getAttributeValuesList(getSubunitType(physicalEntityInst.getSchemClass()))) {
            subInstanceProteins.addAll(getBiologicalProcessProteins(subunitInst));
        }
        return subInstanceProteins;
    }

    /**
     * Determines what subunit type this instance has.
     * @param physicalEntitySchemaClass
     * @return
     */
    private static String getSubunitType(SchemaClass physicalEntitySchemaClass) {
        String subunitType = null;
        if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.Complex)) {
            subunitType = ReactomeJavaConstants.hasComponent;
        } else if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.Polymer)) {
            subunitType = ReactomeJavaConstants.repeatedUnit;
        } else if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.EntitySet)) {
            subunitType = ReactomeJavaConstants.hasMember;
        }
        return subunitType;
    }

    /**
     * Iterates through all retrieves proteins, filtering out any that are invalid, are from the excluded species.
     * @param proteinInstances -- These can be all catalyst proteins or, if no catalyst exists in the reaction, all reaction proteins.
     * @param reactionInst
     * @throws Exception
     */
    private static void processProteins(Set<GKInstance> proteinInstances, GKInstance reactionInst) throws Exception {

        for (GKInstance proteinInst : proteinInstances) {
            GKInstance referenceEntityInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            GKInstance speciesInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.species);
            // Check if the protein has any disqualifying attributes.
            boolean validProtein = GOAGeneratorUtilities.validateProtein(referenceEntityInst, speciesInst);
            if (validProtein) {
                String taxonIdentifier = ((GKInstance) speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
                if (!GOAGeneratorUtilities.excludedMicrobialSpecies(taxonIdentifier)) {
                    getGOBiologicalProcessLine(referenceEntityInst, reactionInst, taxonIdentifier);
                } else {
                    logger.info("Protein is from an excluded microbial species, skipping GO annotation");
                }
            } else {
                logger.info("Invalid protein, skipping GO annotation");
            }
        }
    }

    /**
     * Before creating GOA line for BP annotations, the reaction in question needs to be checked for the existance of a 'goBiologicalProcess' attribute. If there is none
     * than the instance's 'hasEvent' referrals are checked for any.
     * @param referenceEntityInst -- ReferenceEntity instance from protein instance.
     * @param reactionInst
     * @param taxonIdentifier -- CrossReference ID of protein's species.
     * @throws Exception
     */
    private static void getGOBiologicalProcessLine(GKInstance referenceEntityInst, GKInstance reactionInst, String taxonIdentifier) throws Exception {
        for (Map<String, String> biologicalProcessAccession : getGOBiologicalProcessAccessions(reactionInst, 0)) {
            String goaLine = GOAGeneratorUtilities.generateGOALine(referenceEntityInst, GOAGeneratorConstants.BIOLOGICAL_PROCESS_LETTER, biologicalProcessAccession.get(GOAGeneratorConstants.ACCESSION_STRING), biologicalProcessAccession.get(GOAGeneratorConstants.EVENT_STRING),  GOAGeneratorConstants.TRACEABLE_AUTHOR_STATEMENT_CODE, taxonIdentifier);
            GOAGeneratorUtilities.assignDateForGOALine(reactionInst, goaLine);
        }
    }

    /**
     * This method checks for a populated 'goBiologicalProcess' attribute in the incoming instance. If there are none and the max recursion has been reached,
     * it's 'hasEvent' referral is checked for it. Once finding it, it returns the 'accession' and 'identifier' for each one, which will be used to generate a GOA line.
     * @param eventInst -- Can be the original reaction instance, or, if it had no Biological Process accessions, its Event referrals.
     * @param recursion -- Indicates number of times the method has been recursively called.
     * @return
     * @throws Exception
     */
    private static List<Map<String, String>> getGOBiologicalProcessAccessions(GKInstance eventInst, int recursion) throws Exception {
        List<Map<String, String>> goBiologicalProcessAccessions = new ArrayList<>();
        if (recursion <= MAX_RECURSION_LEVEL) {
            Collection<GKInstance> goBiologicalProcessInstances = eventInst.getAttributeValuesList(ReactomeJavaConstants.goBiologicalProcess);
            if (goBiologicalProcessInstances.size() > 0) {
                for (GKInstance goBiologicalProcessInst : goBiologicalProcessInstances) {
                    if (!GOAGeneratorUtilities.proteinBindingAnnotation(goBiologicalProcessInst.getAttributeValue(ReactomeJavaConstants.accession))) {
                        Map<String, String> goBiologicalProcessAccession = new HashMap<>();
                        goBiologicalProcessAccession.put(GOAGeneratorConstants.ACCESSION_STRING, GOAGeneratorConstants.GO_IDENTIFIER_PREFIX + goBiologicalProcessInst.getAttributeValue(ReactomeJavaConstants.accession).toString());
                        GKInstance eventStableIdentifierInst = (GKInstance) eventInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                        goBiologicalProcessAccession.put(GOAGeneratorConstants.EVENT_STRING, GOAGeneratorConstants.REACTOME_IDENTIFIER_PREFIX + eventStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
                        goBiologicalProcessAccessions.add(goBiologicalProcessAccession);
                    } else {
                        logger.info("Accession is for protein binding, skipping GO annotation");
                    }
                }
            } else {
                recursion++;
                Collection<GKInstance> hasEventReferralInstances = (Collection<GKInstance>) eventInst.getReferers(ReactomeJavaConstants.hasEvent);
                if (hasEventReferralInstances != null) {
                    for (GKInstance hasEventReferralInst : hasEventReferralInstances) {
                        goBiologicalProcessAccessions.addAll(getGOBiologicalProcessAccessions(hasEventReferralInst, recursion));
                    }
                }
            }
        }
        return goBiologicalProcessAccessions;
    }

}
