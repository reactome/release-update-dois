package org.reactome.orthoinference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.pathwaylayout.PredictedPathwayDiagramGeneratorFromDB;
import org.gk.persistence.MySQLAdaptor;

import java.util.Collection;

public class OrthologousPathwayDiagramGenerator {

    private static final Logger logger = LogManager.getLogger();

    private MySQLAdaptor dba;
    private MySQLAdaptor dbaPrev;
    private GKInstance speciesInst;
    private long personId;
    private long referenceSpeciesId;

    public OrthologousPathwayDiagramGenerator(MySQLAdaptor dba, MySQLAdaptor dbaPrev, GKInstance speciesInst, long personId, long referenceSpeciesId) {
        this.dba = dba;
        this.dbaPrev = dbaPrev;
        this.speciesInst = speciesInst;
        this.personId = personId;
        this.referenceSpeciesId = referenceSpeciesId;
    }

    /**
     * This method will go through all reference species PathwayDiagrams and finds the orthologous Pathway instances that are for the current target species and
     * generates the orthologous PathwayDiagram via the PredictedPathwayDiagramGeneratorFromDB method in CuratorTool.
     * @throws Exception -- MySQLAdaptor exception
     */
    public void generateOrthologousPathwayDiagrams() throws Exception {

        logger.info("Generating pathway diagrams for inferred " + speciesInst.getDisplayName() + " pathways");
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
                        // This method is the one needed to build PathwayDiagrams for species-specific Pathway instances.
                        logger.info("Building Pathway diagram for " + orthoPathwayInst);
                        diagramGenerator.generatePredictedDiagram(orthoPathwayInst, pathwayInst, diagramInst);
                    }
                }
            }
        }

        comparePathwayDiagramCounts((Collection<GKInstance>) dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram), (Collection<GKInstance>) dbaPrev.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram));
        logger.info("Finish pathway diagram generation for " + speciesInst.getDisplayName());
    }

    // This method checks that the PathwayDiagram count has not decreased since previous release.
    private void comparePathwayDiagramCounts(Collection<GKInstance> currentPathwayDiagramInstances, Collection<GKInstance> previousPathwayDiagramInstances) throws Exception {

        int currentPathwayDiagramCount = getPathwayDiagramCountsForSpecies(currentPathwayDiagramInstances);
        int previousPathwayDiagramCount = getPathwayDiagramCountsForSpecies(previousPathwayDiagramInstances);

        currentPathwayDiagramCount--;
        if (currentPathwayDiagramCount < previousPathwayDiagramCount) {
            logger.warn("PathwayDiagram count for " + speciesInst.getDisplayName() + " has decreased since previous release from " + previousPathwayDiagramCount + " to " + currentPathwayDiagramCount);
        }
    }

    // This method retrieves the PathwayDiagram instance count for a specific species
    private int getPathwayDiagramCountsForSpecies(Collection<GKInstance> PathwayDiagramInstances) throws Exception {
        int pathwayDiagramCount = 0;
        for (GKInstance pathwayDiagramInst : PathwayDiagramInstances) {
            GKInstance pathwayInst = (GKInstance) pathwayDiagramInst.getAttributeValue(ReactomeJavaConstants.representedPathway);
            GKInstance pathwaySpeciesInst = (GKInstance) pathwayInst.getAttributeValue(ReactomeJavaConstants.species);
            if (speciesInst.getDBID().equals(pathwaySpeciesInst.getDBID())) {
                pathwayDiagramCount++;
            }
        }
        return pathwayDiagramCount;
    }
}
