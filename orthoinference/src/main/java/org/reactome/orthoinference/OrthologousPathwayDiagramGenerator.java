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
    private GKInstance targetSpeciesInst;
    private long personId;
    private long referenceSpeciesId;

    public OrthologousPathwayDiagramGenerator(MySQLAdaptor dba, MySQLAdaptor dbaPrev, GKInstance targetSpeciesInst, long personId, long referenceSpeciesId) {
        this.dba = dba;
        this.dbaPrev = dbaPrev;
        this.targetSpeciesInst = targetSpeciesInst;
        this.personId = personId;
        this.referenceSpeciesId = referenceSpeciesId;
    }

    /**
     * This method will go through all reference species' PathwayDiagrams and finds the orthologous Pathway instances that are for the current target species and
     * generates the orthologous PathwayDiagram via the PredictedPathwayDiagramGeneratorFromDB method in the CuratorTool repository.
     * @throws Exception -- MySQLAdaptor exception
     */
    public void generateOrthologousPathwayDiagrams() throws Exception {

        logger.info("Generating pathway diagrams for inferred " + targetSpeciesInst.getDisplayName() + " pathways");
        // Create PredictedPathwayDiagramGeneratorFromDB object and set db adaptor and author ID.
        PredictedPathwayDiagramGeneratorFromDB diagramGenerator = new PredictedPathwayDiagramGeneratorFromDB();
        diagramGenerator.setMySQLAdaptor(dba);
        diagramGenerator.setDefaultPersonId(personId);

        GKInstance referenceSpeciesInst = dba.fetchInstance(referenceSpeciesId);

        // Iterate through each PathwayDiagram instance looking for those associated with the reference species.
        for (GKInstance diagramInst: (Collection<GKInstance>) dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram)) {
            GKInstance pathwayInst = (GKInstance) diagramInst.getAttributeValue(ReactomeJavaConstants.representedPathway);
            if (isSameSpecies(pathwayInst, referenceSpeciesInst)) {
                // When a PathwayDiagram instance associated with the reference species is found, iterate through all of it's OrthologousEvent instances.
                for (GKInstance orthoPathwayInst : (Collection<GKInstance>) pathwayInst.getAttributeValuesList(ReactomeJavaConstants.orthologousEvent)) {
                    // Look for OrthologousEvent instances that match the current target species and that are electronically inferred.
                    GKInstance orthoDiagram = generateOrthologousPathwayDiagram(orthoPathwayInst, pathwayInst, diagramInst, diagramGenerator);
                }
            }
        }

        comparePathwayDiagramCounts((Collection<GKInstance>) dba.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram), (Collection<GKInstance>) dbaPrev.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram));
        logger.info("Finish pathway diagram generation for " + targetSpeciesInst.getDisplayName());
    }

    public GKInstance generateOrthologousPathwayDiagram(GKInstance orthoPathwayInst, GKInstance pathwayInst, GKInstance diagramInst, PredictedPathwayDiagramGeneratorFromDB diagramGenerator) throws Exception {
        GKInstance orthoDiagram = null;
        if (isSameSpecies(orthoPathwayInst, targetSpeciesInst) && isElectronicallyInferred(orthoPathwayInst)) {
            // Generate Orthologous PathwayDiagram instance using generatePredictedDiagram method from PredictedPathwayDiagramGeneratorFromDB.
            // This method is the one needed to build PathwayDiagrams for species-specific Pathway instances.
            logger.info("Building Pathway diagram for " + orthoPathwayInst);
            orthoDiagram = diagramGenerator.generatePredictedDiagram(orthoPathwayInst, pathwayInst, diagramInst);
        }
        return orthoDiagram;
    }

    // Compare the species attribute in a Pathway with another species instance for equality
    public boolean isSameSpecies(GKInstance pathwayInst, GKInstance speciesInst) throws Exception {
        GKInstance pathwaySpeciesInst = (GKInstance) pathwayInst.getAttributeValue(ReactomeJavaConstants.species);
        return pathwaySpeciesInst.equals(speciesInst);
    }

    // Check if instance is electronically inferred via the evidenceType attribute. Only inferred instances have it populated.
    public boolean isElectronicallyInferred(GKInstance orthoPathwayInst) throws Exception {
        return orthoPathwayInst.getAttributeValue(ReactomeJavaConstants.evidenceType) != null;

    }

    // This method checks that the PathwayDiagram count has not decreased since previous release.
    private void comparePathwayDiagramCounts(Collection<GKInstance> currentPathwayDiagramInstances, Collection<GKInstance> previousPathwayDiagramInstances) throws Exception {

        int currentPathwayDiagramCount = getPathwayDiagramCountsForSpecies(currentPathwayDiagramInstances);
        int previousPathwayDiagramCount = getPathwayDiagramCountsForSpecies(previousPathwayDiagramInstances);

        if (hasFewerSpeciesDiagramCountsBetweenReleases(currentPathwayDiagramCount, previousPathwayDiagramCount)) {
            logger.warn("PathwayDiagram count for " + targetSpeciesInst.getDisplayName() + " has decreased since previous release from " + previousPathwayDiagramCount + " to " + currentPathwayDiagramCount);
        }
    }

    public boolean hasFewerSpeciesDiagramCountsBetweenReleases(int currentPathwayDiagramCount, int previousPathwayDiagramCount) {
        return currentPathwayDiagramCount < previousPathwayDiagramCount;
    }

    // This method retrieves the PathwayDiagram instance count for a specific species
    private int getPathwayDiagramCountsForSpecies(Collection<GKInstance> PathwayDiagramInstances) throws Exception {
        int pathwayDiagramCount = 0;
        for (GKInstance pathwayDiagramInst : PathwayDiagramInstances) {
            GKInstance pathwayInst = (GKInstance) pathwayDiagramInst.getAttributeValue(ReactomeJavaConstants.representedPathway);
            GKInstance pathwaySpeciesInst = (GKInstance) pathwayInst.getAttributeValue(ReactomeJavaConstants.species);
            if (targetSpeciesInst.getDBID().equals(pathwaySpeciesInst.getDBID())) {
                pathwayDiagramCount++;
            }
        }
        return pathwayDiagramCount;
    }
}
