package org.reactome.orthoinference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.pathwaylayout.PredictedPathwayDiagramGeneratorFromDB;
import org.gk.persistence.MySQLAdaptor;

import java.util.Collection;

public class PathwayDiagramGenerator {

    private static final Logger logger = LogManager.getLogger();

    private static MySQLAdaptor dba;
    private static GKInstance speciesInst;
    private static long personId;
    private static long referenceSpeciesId;
    public static void generateOrthologousPathwayDiagrams() throws Exception {

        logger.info("Generating diagrams for inferred " + speciesInst.getDisplayName() + "pathways");
        PredictedPathwayDiagramGeneratorFromDB diagramGenerator = new PredictedPathwayDiagramGeneratorFromDB();
        diagramGenerator.setMySQLAdaptor(dba);
        diagramGenerator.setDefaultPersonId(personId);

        GKInstance referenceSpeciesInst = dba.fetchInstance(referenceSpeciesId);

        for (GKInstance diagramInst: (Collection<GKInstance>) dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram)) {
            GKInstance pathwayInst = (GKInstance) diagramInst.getAttributeValue(ReactomeJavaConstants.representedPathway);
            GKInstance sourceSpeciesInst = (GKInstance) pathwayInst.getAttributeValue(ReactomeJavaConstants.species);
            if (sourceSpeciesInst == referenceSpeciesInst) {
                for (GKInstance orthoPathwayInst : (Collection<GKInstance>) pathwayInst.getAttributeValuesList(ReactomeJavaConstants.orthologousEvent)) {
                    GKInstance targetSpeciesInst = (GKInstance) orthoPathwayInst.getAttributeValue(ReactomeJavaConstants.species);
                    if (targetSpeciesInst == speciesInst && orthoPathwayInst.getAttributeValue(ReactomeJavaConstants.evidenceType) != null) {
                        logger.info("Building Pathway diagram for " + orthoPathwayInst);
                        diagramGenerator.generatePredictedDiagram(orthoPathwayInst, pathwayInst, diagramInst);
                    }
                }
            }
        }
        logger.info("Finish pathway diagram generation for " + speciesInst.getDisplayName());
    }

    public static void setAdaptor(MySQLAdaptor dbAdaptor) {
        dba = dbAdaptor;
    }

    public static void setSpeciesInstance(GKInstance speciesInstCopy) {
        speciesInst = speciesInstCopy;
    }

    public static void setPersonId(long personIdCopy) {
        personId = personIdCopy;
    }

    public static void setReferenceSpeciesId(long referenceSpeciesIdCopy) {
        referenceSpeciesId = referenceSpeciesIdCopy;
    }
}
