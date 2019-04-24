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

    /**
     * This method will go through all reference species PathwayDiagrams and finds the orthologous Pathway instances that are for the current target species and
     * generates the orthologous PathwayDiagram via the PredictedPathwayDiagramGeneratorFromDB method in CuratorTool.
     * @throws Exception
     */
    public static void generateOrthologousPathwayDiagrams() throws Exception {

        logger.info("Generating diagrams for inferred " + speciesInst.getDisplayName() + "pathways");
        // Create PredictedPathwayDiagramGeneratorFromDB object and set db adaptor and author ID.
        PredictedPathwayDiagramGeneratorFromDB diagramGenerator = new PredictedPathwayDiagramGeneratorFromDB();
        diagramGenerator.setMySQLAdaptor(dba);
        diagramGenerator.setDefaultPersonId(personId);

        GKInstance referenceSpeciesInst = dba.fetchInstance(referenceSpeciesId);

        // Iterate through each PathwayDiagram instance looking for those associated with the reference species.
        for (GKInstance diagramInst: (Collection<GKInstance>) dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram)) {
            GKInstance pathwayInst = (GKInstance) diagramInst.getAttributeValue(ReactomeJavaConstants.representedPathway);
            GKInstance sourceSpeciesInst = (GKInstance) pathwayInst.getAttributeValue(ReactomeJavaConstants.species);
            if (sourceSpeciesInst == referenceSpeciesInst) {
                // When a PathwayDiagram instance associated with the reference species is found, iterate through all of it's OrthologousEvent instances.
                for (GKInstance orthoPathwayInst : (Collection<GKInstance>) pathwayInst.getAttributeValuesList(ReactomeJavaConstants.orthologousEvent)) {
                    GKInstance targetSpeciesInst = (GKInstance) orthoPathwayInst.getAttributeValue(ReactomeJavaConstants.species);
                    // Look for OrthologousEvent instances that match the current target species and that are electronically inferred.
                    if (targetSpeciesInst == speciesInst && orthoPathwayInst.getAttributeValue(ReactomeJavaConstants.evidenceType) != null) {
                        // Generate Orthologous PathwayDiagram instance using generatePredictedDiagram method from PredictedPathwayDiagramGeneratorFromDB.
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
