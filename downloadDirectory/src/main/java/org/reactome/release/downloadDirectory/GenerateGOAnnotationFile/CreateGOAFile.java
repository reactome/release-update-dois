package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class CreateGOAFile {

    private static final Logger logger = LogManager.getLogger();
    private static final String GOA_FILENAME = "gene_association.reactome";

    public static void execute(MySQLAdaptor dbAdaptor, String releaseNumber) throws Exception {
        logger.info("Generating GO annotation file: gene_association.reactome");

        for (GKInstance reactionInst : (Collection<GKInstance>) dbAdaptor.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent)) {
            // Only finding GO accessions from curated ReactionlikeEvents
            if (!isInferred(reactionInst)) {
                CellularComponentAnnotationBuilder.processCellularComponents(reactionInst);
                MolecularFunctionAnnotationBuilder.processMolecularFunctions(reactionInst);
                BiologicalProcessAnnotationBuilder.processBiologicalFunctions(reactionInst);
            }
        }
        GOAGeneratorUtilities.outputGOAFile(GOA_FILENAME);
        Files.move(Paths.get(GOA_FILENAME), Paths.get(releaseNumber + "/" + GOA_FILENAME), StandardCopyOption.REPLACE_EXISTING);
        logger.info("Finished generating gene_association.reactome");
        }

    // Parent method that houses electronically and manually inferred instance checks.
    private static boolean isInferred(GKInstance reactionInst) throws Exception {
        if (isElectronicallyInferred(reactionInst) || isManuallyInferred(reactionInst)) {
            return true;
        }
        return false;
    }

    private static boolean isManuallyInferred(GKInstance reactionInst) throws Exception {
        return reactionInst.getAttributeValue(ReactomeJavaConstants.inferredFrom) != null;
    }

    private static boolean isElectronicallyInferred(GKInstance reactionInst) throws Exception {
        return reactionInst.getAttributeValue(ReactomeJavaConstants.evidenceType) != null;
    }
}
