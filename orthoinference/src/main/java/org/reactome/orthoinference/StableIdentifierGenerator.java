package org.reactome.orthoinference;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;

import java.util.HashMap;
import java.util.Map;
import static org.gk.model.ReactomeJavaConstants.*;

/*
 *  All PhysicalEntitys, ReactionlikeEvents and Pathways are routed to this class to generate their stable identifiers
 */
public class StableIdentifierGenerator {

    private static MySQLAdaptor dba;
    private static GKInstance instanceEditInst;
    private static Map<String,Integer> seenOrthoIds = new HashMap<>();
    private static String speciesAbbreviation = null;

    public static GKInstance generateOrthologousStableId(GKInstance inferredInst, GKInstance originalInst) throws Exception {

        // All Human PhysicalEntitys and Events will have a StableIdentifier instance in the stableIdentifier attribute
        GKInstance stableIdentifierInst = (GKInstance) originalInst.getAttributeValue(stableIdentifier);

        // For now, Human is hard-coded as the source species, so we replace the stableIdentifier source species based on that assumption
        String sourceIdentifier = (String) stableIdentifierInst.getAttributeValue(identifier);
        String targetIdentifier = sourceIdentifier.replace("HSA", speciesAbbreviation);
        // Paralogs will have the same base stable identifier, but we want to denote when that happens.
        // We pull the value from `seenOrthoIds`, increment it and then add it to the stable identifier name (eg: R-MMU-123456-2)
        if (seenOrthoIds.get(targetIdentifier) == null) {
            seenOrthoIds.put(targetIdentifier, 1);
        } else {
            int paralogCount = seenOrthoIds.get(targetIdentifier);
            paralogCount++;
            seenOrthoIds.put(targetIdentifier, paralogCount);
            targetIdentifier += "-" + paralogCount;
        }

        // Create new StableIdentifier instance
        GKInstance orthoStableIdentifierInst = InstanceUtilities.createNewInferredGKInstance(stableIdentifierInst);
        orthoStableIdentifierInst.addAttributeValue(identifier, targetIdentifier);
        String identifierVersionNumber = "1";
        orthoStableIdentifierInst.addAttributeValue(identifierVersion, identifierVersionNumber);
        String orthoStableIdentifierName = targetIdentifier + "." + identifierVersionNumber;
        orthoStableIdentifierInst.setDisplayName(orthoStableIdentifierName);
        dba.storeInstance(orthoStableIdentifierInst);

        // Populate inferred instance with new StableIdentifier instance
        inferredInst.addAttributeValue(stableIdentifier, orthoStableIdentifierInst);

        return inferredInst;

    }

    public static void setSpeciesAbbreviation(String speciesAbbreviationCopy) {
        speciesAbbreviation = speciesAbbreviationCopy;
    }

    public static void setInstanceEdit(GKInstance instanceEditInstCopy) {
        instanceEditInst = instanceEditInstCopy;
    }

    public static void setAdaptor(MySQLAdaptor dbAdaptor) {
        dba = dbAdaptor;
    }
}
