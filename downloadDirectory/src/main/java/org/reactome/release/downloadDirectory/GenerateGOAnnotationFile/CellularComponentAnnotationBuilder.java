package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;


import java.util.*;

public class CellularComponentAnnotationBuilder {

    private static final Logger logger = LogManager.getLogger();

    private static final List<String> speciesWithAlternateGOCompartment = new ArrayList<>(Arrays.asList("11676", "211044", "1491", "1392"));
    private static final String CELLULAR_COMPONENT_LETTER = "C";

    public static void processCellularComponents(GKInstance reactionInst) throws Exception {
        Set<GKInstance> proteinInstances = GOAGeneratorUtilities.retrieveProteins(reactionInst);
        processProteins(proteinInstances, reactionInst);
    }

    private static void processProteins(Set<GKInstance> proteinInstances, GKInstance reactionInst) throws Exception {
        for (GKInstance proteinInst : proteinInstances) {
            GKInstance referenceEntityInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            GKInstance speciesInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.species);
            // Check if the protein has any disqualifying attributes.
            boolean validProtein = GOAGeneratorUtilities.validateProtein(referenceEntityInst, speciesInst);
            if (validProtein) {
                String taxonIdentifier = ((GKInstance) speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
                if (!GOAGeneratorUtilities.excludedMicrobialSpecies(taxonIdentifier)) {

                    // For CC annotations, alternateGOCompartments are disqualifying.
                    if (!speciesWithAlternateGOCompartment.contains(taxonIdentifier)) {
                        // For CC, we are looking at ALL proteins in a ReactionlikeEvent
                        generateGOCellularCompartmentLine(proteinInst, referenceEntityInst, reactionInst, taxonIdentifier);
                    } else {
                        logger.info("Protein has an alternate GO compartment, skipping GO annotation");
                    }
                } else {
                    logger.info("Protein is from an excluded microbial species, skipping GO annotation");
                }
            } else {
                logger.info("Invalid protein, skipping GO annotation");
            }
        }
    }

    // This feeds into the GOA line generator. It formats that method call with attributes specific to CC annotations.
    private static void generateGOCellularCompartmentLine(GKInstance proteinInst, GKInstance referenceEntityInst, GKInstance reactionInst, String taxonIdentifier) throws Exception {
        String reactomeIdentifier = "REACTOME:" + ((GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
        String goCellularCompartmentAccession = getCellularCompartmentGOAccession(proteinInst);
        if (!goCellularCompartmentAccession.isEmpty()) {
            String goaLine = GOAGeneratorUtilities.generateGOALine(referenceEntityInst, CELLULAR_COMPONENT_LETTER, goCellularCompartmentAccession, reactomeIdentifier, "TAS", taxonIdentifier);
            GOAGeneratorUtilities.assignDateForGOALine(proteinInst, goaLine);
        } else {
            logger.info("Protein has an empty compartment, skipping GO annotation");
        }
    }

    // Checks for and returns valid GO accessions specific to Cellular Compartments in the protein of interest.
    private static String getCellularCompartmentGOAccession(GKInstance proteinInst) throws Exception {
        GKInstance compartmentInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.compartment);
        String cellularComponentAccession = "";
        if (compartmentInst != null && !GOAGeneratorUtilities.proteinBindingAnnotation(compartmentInst.getAttributeValue(ReactomeJavaConstants.accession))) {
            cellularComponentAccession = "GO:" + compartmentInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
        }
        return cellularComponentAccession;
    }

}
