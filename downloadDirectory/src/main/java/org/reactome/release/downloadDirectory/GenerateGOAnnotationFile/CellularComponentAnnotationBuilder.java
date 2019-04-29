package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;


import java.util.*;

public class CellularComponentAnnotationBuilder {

    private static final Logger logger = LogManager.getLogger();

    // CrossReference IDs of species containing an alternative GO compartment, which do not receive a GO annotation: HIV 1, unknown, C. botulinum, B. anthracis.
    private static final List<String> speciesWithAlternateGOCompartment = Arrays.asList(GOAGeneratorConstants.HIV_1_CROSS_REFERENCE, GOAGeneratorConstants.C_BOTULINUM_CROSS_REFERENCE, GOAGeneratorConstants.B_ANTHRACIS_CROSS_REFERENCE);

    /**
     * Initial Cellular Compartment annotations method that first retrieves all proteins associated with the reaction before moving to GO annotation.
     * Then iterates through each retrieved protein, filtering out any that are invalid or are from the excluded species.
     * @param reactionInst -- GKInstance from ReactionlikeEvent class.
     * @throws Exception -- MySQLAdaptor exception.
     */
    public static void processCellularComponents(GKInstance reactionInst) throws Exception {
        // First retrieve proteins, then build GO annotation
        for (GKInstance proteinInst : GOAGeneratorUtilities.retrieveProteins(reactionInst)) {
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
     * @param proteinInst -- GKInstance, Individual protein instance from the retrieved proteins.
     * @param referenceEntityInst -- GKInstance, Reference Entity instance of the protein.
     * @param reactionInst -- GKInstance, Parent reaction that the protein is associated with.
     * @param taxonIdentifier -- String, CrossReference ID of protein's species.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static void generateGOCellularCompartmentLine(GKInstance proteinInst, GKInstance referenceEntityInst, GKInstance reactionInst, String taxonIdentifier) throws Exception {
        String reactomeIdentifier = GOAGeneratorConstants.REACTOME_IDENTIFIER_PREFIX  + ((GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
        String goCellularCompartmentAccession = getCellularCompartmentGOAccession(proteinInst);
        if (!goCellularCompartmentAccession.isEmpty()) {
            String goaLine = GOAGeneratorUtilities.generateGOALine(referenceEntityInst, GOAGeneratorConstants.CELLULAR_COMPONENT_LETTER, goCellularCompartmentAccession, reactomeIdentifier,  GOAGeneratorConstants.TRACEABLE_AUTHOR_STATEMENT_CODE, taxonIdentifier);
            GOAGeneratorUtilities.assignDateForGOALine(proteinInst, goaLine);
        } else {
            logger.info("Protein has no Cellular Compartment accession, skipping GO annotation");
        }
    }

    /**
     * Checks for and returns valid GO accession specific to Cellular Compartments in the protein of interest.
     * @param proteinInst -- GKInstance, Individual protein instance from the retrieved proteins.
     * @return -- String, GO Cellular Compartment accession from proteinInst.
     * @throws Exception -- MySQLAdaptor exception.
     */
    private static String getCellularCompartmentGOAccession(GKInstance proteinInst) throws Exception {
        GKInstance compartmentInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.compartment);
        String cellularComponentAccession = "";
        if (compartmentInst != null && !GOAGeneratorUtilities.proteinBindingAnnotation(compartmentInst.getAttributeValue(ReactomeJavaConstants.accession))) {
            cellularComponentAccession = GOAGeneratorConstants.GO_IDENTIFIER_PREFIX + compartmentInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
        }
        return cellularComponentAccession;
    }

}
