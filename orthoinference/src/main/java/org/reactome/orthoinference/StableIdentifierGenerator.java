package org.reactome.orthoinference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.gk.model.ReactomeJavaConstants.*;

/*
 *  All PhysicalEntitys, ReactionlikeEvents and Pathways are routed to this class to generate their stable identifiers
 */
public class StableIdentifierGenerator {
    private static final Logger logger = LogManager.getLogger();
    private static Map<String,Integer> seenOrthoIds = new HashMap<>();

    private MySQLAdaptor dba;
    private String speciesAbbreviation;

    public StableIdentifierGenerator(MySQLAdaptor dba, String speciesAbbreviation) {
        this.dba = dba;
        this.speciesAbbreviation = speciesAbbreviation;
    }

    public GKInstance generateOrthologousStableId(GKInstance inferredInst, GKInstance originalInst) throws Exception {

        // All Human PhysicalEntitys and Events will have a StableIdentifier instance in the stableIdentifier attribute
        GKInstance stableIdentifierInst = (GKInstance) originalInst.getAttributeValue(stableIdentifier);
        if (stableIdentifierInst == null) {
            String missingStableIdentifierMsg = "No stable identifier instance found for " + originalInst;
            logger.fatal(missingStableIdentifierMsg);
            throw new RuntimeException(missingStableIdentifierMsg);
        }

        // For now, Human is hard-coded as the source species, so we replace the stableIdentifier source species based on that assumption
        String sourceIdentifier = (String) stableIdentifierInst.getAttributeValue(identifier);
        String targetIdentifier = sourceIdentifier.replace("HSA", speciesAbbreviation);
        // Paralogs will have the same base stable identifier, but we want to denote when that happens.
        // We pull the value from `seenOrthoIds`, increment it and then add it to the stable identifier name (eg: R-MMU-123456-2)
        int paralogCount = Optional.ofNullable(seenOrthoIds.get(targetIdentifier)).orElse(0) + 1;
        seenOrthoIds.put(targetIdentifier, paralogCount);
        if (paralogCount > 1) {
            targetIdentifier += "-" + paralogCount;
        }


        // Check that the stable identifier instance does not already exist in DB
        // TODO: Performance check
        Collection<GKInstance> existingStableIdentifier = (Collection<GKInstance>) dba.fetchInstanceByAttribute("StableIdentifier", "identifier", "=", targetIdentifier);

        GKInstance orthoStableIdentifierInst;
        if (existingStableIdentifier.size() == 0) {
            // Create new StableIdentifier instance
            orthoStableIdentifierInst = createOrthologousStableIdentifierInstance(stableIdentifierInst, targetIdentifier);
            dba.storeInstance(orthoStableIdentifierInst);
        } else {
            orthoStableIdentifierInst = existingStableIdentifier.iterator().next();
        }

        // Populate inferred instance with new StableIdentifier instance
        inferredInst.addAttributeValue(stableIdentifier, orthoStableIdentifierInst);
        return inferredInst;
    }

    private GKInstance createOrthologousStableIdentifierInstance(GKInstance stableIdentifierInst, String targetIdentifier) throws Exception {
        GKInstance orthoStableIdentifierInst = InstanceUtilities.createNewInferredGKInstance(stableIdentifierInst);
        orthoStableIdentifierInst.addAttributeValue(identifier, targetIdentifier);
        String identifierVersionNumber = "1";
        orthoStableIdentifierInst.addAttributeValue(identifierVersion, identifierVersionNumber);
        String orthoStableIdentifierName = targetIdentifier + "." + identifierVersionNumber;
        orthoStableIdentifierInst.setDisplayName(orthoStableIdentifierName);
        return orthoStableIdentifierInst;
    }
}
