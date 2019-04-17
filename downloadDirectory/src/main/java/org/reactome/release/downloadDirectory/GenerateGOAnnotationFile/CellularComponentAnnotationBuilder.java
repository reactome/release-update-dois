package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;


import java.util.*;

public class CellularComponentAnnotationBuilder {

    private static final Logger logger = LogManager.getLogger();

    // CrossReference IDs of species containing an alternative GO compartment, which do not receive a GO annotation: HIV 1, unknown, C. botulinum, B. anthracis
    private static final List<String> speciesWithAlternateGOCompartment = new ArrayList<>(Arrays.asList("11676", "211044", "1491", "1392"));
    private static final String CELLULAR_COMPONENT_LETTER = "C";

    /**
     * Initial Cellular Compartment annotations method that first retrieves all proteins associated with the reaction before moving to GO annotation.
     * @param reactionInst
     * @throws Exception
     */
    public static void processCellularComponents(GKInstance reactionInst) throws Exception {
        Set<GKInstance> proteinInstances = GOAGeneratorUtilities.retrieveProteins(reactionInst);
        processProteins(proteinInstances, reactionInst);
    }

    /**
     * Iterates through each retrieved protein, filtering out any that are invalid or are from the excluded species.
     * @param proteinInstances -- Proteins retrieved from the above 'retrieveProteins' step.
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
                // For CC annotations, alternateGOCompartments are disqualifying.
                if (!GOAGeneratorUtilities.excludedMicrobialSpecies(taxonIdentifier)) {
                    if (!speciesWithAlternateGOCompartment.contains(taxonIdentifier)) {
                        // For CC, we are looking at ALL proteins in a ReactionlikeEvent
                        generateGOCellularCompartmentLine(proteinInst, referenceEntityInst, reactionInst, taxonIdentifier);
                    } else {
                        logger.info("Species has an alternative GO compartment, skipping GO annotation");
                    }
                } else {
                    logger.info("Protein is from an excluded microbial species, skipping GO annotation");
                }
            } else {
                logger.info("Invalid protein, skipping GO annotation");
            }
        }
    }

    /**
     * Proteins reaching this part of the script are deemed valid for GO annotation. Retrieves the Cellular Compartment accession associated with this instance and then calls the GOA line generator.
     * @param proteinInst -- Individual protein instance from the retrieved proteins.
     * @param referenceEntityInst -- Reference Entity instance of the protein.
     * @param reactionInst -- Parent reaction that the protein is assocaited with.
     * @param taxonIdentifier -- CrossReference ID of protein's species
     * @throws Exception
     */
    private static void generateGOCellularCompartmentLine(GKInstance proteinInst, GKInstance referenceEntityInst, GKInstance reactionInst, String taxonIdentifier) throws Exception {
        String reactomeIdentifier = "REACTOME:" + ((GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
        String goCellularCompartmentAccession = getCellularCompartmentGOAccession(proteinInst);
        if (!goCellularCompartmentAccession.isEmpty()) {
            String goaLine = GOAGeneratorUtilities.generateGOALine(referenceEntityInst, CELLULAR_COMPONENT_LETTER, goCellularCompartmentAccession, reactomeIdentifier, "TAS", taxonIdentifier);
            GOAGeneratorUtilities.assignDateForGOALine(proteinInst, goaLine);
        } else {
            logger.info("Protein has no Cellular Compartment accession, skipping GO annotation");
        }
    }

    /**
     * Checks for and returns valid GO accession specific to Cellular Compartments in the protein of interest.
     * @param proteinInst
     * @return
     * @throws Exception
     */
    private static String getCellularCompartmentGOAccession(GKInstance proteinInst) throws Exception {
        GKInstance compartmentInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.compartment);
        String cellularComponentAccession = "";
        if (compartmentInst != null && !GOAGeneratorUtilities.proteinBindingAnnotation(compartmentInst.getAttributeValue(ReactomeJavaConstants.accession))) {
            cellularComponentAccession = "GO:" + compartmentInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
        }
        return cellularComponentAccession;
    }

}
