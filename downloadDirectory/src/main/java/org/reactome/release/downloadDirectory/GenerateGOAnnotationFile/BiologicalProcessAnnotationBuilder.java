package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaClass;

import java.util.*;

public class BiologicalProcessAnnotationBuilder {

    private static final int MAX_RECURSION_LEVEL = 2;

    private static final List<String> microbialSpeciesToExclude = new ArrayList<>(Arrays.asList("813", "562", "491", "90371", "1280", "5811"));
    private static final String PROTEIN_BINDING_ANNOTATION = "0005515";

    public static void processBiologicalFunctions(GKInstance reactionInst) throws Exception {

        Collection<GKInstance> catalystInstances = reactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        if (catalystInstances.size() == 0) {
            Set<GKInstance> proteinInstances = GOAGeneratorUtilities.retrieveProteins(reactionInst);
            processProteins(proteinInstances, reactionInst);
        } else {
            // Check that catalyst instance has no disqualifying attributes.
            for (GKInstance catalystInst : catalystInstances) {
                GKInstance catalystPEInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity);
                boolean validCatalyst = GOAGeneratorUtilities.validateCatalyst(catalystPEInst);
                if (validCatalyst) {
                    Set<GKInstance> proteinInstances = getBiologicalProcessProteins((GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity));
                    processProteins(proteinInstances, reactionInst);
                }
            }
        }
    }

    // Returns all protein instances from multi-instance PhysicalEntitys (Polymer/Complex/EntitySet) or just the single incoming instance if it is an EWAS.
    private static Set<GKInstance> getBiologicalProcessProteins(GKInstance physicalEntityInst) throws Exception {
        Set<GKInstance> proteinInstances = new HashSet<>();
        if (GOAGeneratorUtilities.multiInstancePhysicalEntity(physicalEntityInst.getSchemClass())) {
            proteinInstances.addAll(getMultiInstanceSubInstances(physicalEntityInst));
        } else if (physicalEntityInst.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
            proteinInstances.add(physicalEntityInst);
        }
        return proteinInstances;
    }

    // Attempt to generate GO Annotation information for each protein associated with the whole Reaction or just it's catalysts, depending on the goTerm being evaluated.
    private static void processProteins(Set<GKInstance> proteinInstances, GKInstance reactionInst) throws Exception {

        for (GKInstance proteinInst : proteinInstances) {
            GKInstance referenceEntityInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            GKInstance speciesInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.species);
            // Check if the protein has any disqualifying attributes.
            boolean validProtein = GOAGeneratorUtilities.validateProtein(referenceEntityInst, speciesInst);
            if (validProtein) {
                String taxonIdentifier = ((GKInstance) speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
                // TODO: Rule check
                if (!microbialSpeciesToExclude.contains(taxonIdentifier)) {
                    getGOBiologicalProcessLine(referenceEntityInst, reactionInst, taxonIdentifier);
                }
            }
        }
    }

    // Before creating GOA line for BP annotations, the reaction in question needs to be checked for the existance of a 'goBiologicalProcess' attribute. If there is none
    // than the instance's 'hasEvent' referrals are checked for any.
    private static List<String> getGOBiologicalProcessLine(GKInstance referenceEntityInst, GKInstance reactionInst, String taxonIdentifier) throws Exception {
        List<String> goAnnotationLines = new ArrayList<>();
        for (Map<String, String> biologicalProcessAccession : getGOBiologicalProcessAccessions(reactionInst, 0)) {
            String goaLine = GOAGeneratorUtilities.generateGOALine(referenceEntityInst, "P", biologicalProcessAccession.get("accession"), biologicalProcessAccession.get("event"), "TAS", taxonIdentifier);
            GOAGeneratorUtilities.assignDateForGOALine(reactionInst, goaLine);
            goAnnotationLines.add(goaLine);
        }
        return goAnnotationLines;
    }

    // This method checks for a populated 'goBiologicalProcess' attribute in the incoming instance. If there are none and the max recursion has been reached,
    // it's 'hasEvent' referral is checked for it. Once finding it, it returns the 'accession' and 'identifier' for each one, which will be used to generate a GOA line.
    private static List<Map<String, String>> getGOBiologicalProcessAccessions(GKInstance eventInst, int recursion) throws Exception {
        List<Map<String, String>> goBiologicalProcessAccessions = new ArrayList<>();
        if (recursion <= MAX_RECURSION_LEVEL) {
            Collection<GKInstance> goBiologicalProcessInstances = eventInst.getAttributeValuesList(ReactomeJavaConstants.goBiologicalProcess);
            if (goBiologicalProcessInstances.size() > 0) {
                for (GKInstance goBiologicalProcessInst : goBiologicalProcessInstances) {
                    if (!goBiologicalProcessInst.getAttributeValue(ReactomeJavaConstants.accession).toString().equals(PROTEIN_BINDING_ANNOTATION)) {
                        Map<String, String> goBiologicalProcessAccession = new HashMap<>();
                        goBiologicalProcessAccession.put("accession", "GO:" + goBiologicalProcessInst.getAttributeValue(ReactomeJavaConstants.accession).toString());
                        GKInstance eventStableIdentifierInst = (GKInstance) eventInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                        goBiologicalProcessAccession.put("event", "REACTOME:" + eventStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
                        goBiologicalProcessAccessions.add(goBiologicalProcessAccession);
                    }
                }
            } else {
                recursion++;
                Collection<GKInstance> hasEventReferralInstances = (Collection<GKInstance>) eventInst.getReferers(ReactomeJavaConstants.hasEvent);
                if (hasEventReferralInstances != null) {
                    for (GKInstance hasEventReferralInst : hasEventReferralInstances) {
                        goBiologicalProcessAccessions.addAll(getGOBiologicalProcessAccessions(hasEventReferralInst, recursion));
                    }
                }
            }
        }
        return goBiologicalProcessAccessions;
    }

    // Returns all constituent PhysicalEntity instances of a Multi-instance PhysicalEntity
    private static Set<GKInstance> getMultiInstanceSubInstances(GKInstance physicalEntityInst) throws Exception {
        SchemaClass physicalEntitySchemaClass = physicalEntityInst.getSchemClass();
        String subunitType = null;
        Set<GKInstance> subInstanceProteins = new HashSet<>();
        if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.Complex)) {
            subunitType = ReactomeJavaConstants.hasComponent;
        } else if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.Polymer)) {
            subunitType = ReactomeJavaConstants.repeatedUnit;
        } else if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.EntitySet)) {
            subunitType = ReactomeJavaConstants.hasMember;
        }
        for (GKInstance subunitInst : (Collection<GKInstance>) physicalEntityInst.getAttributeValuesList(subunitType)) {
            subInstanceProteins.addAll(getBiologicalProcessProteins(subunitInst));
        }
        return subInstanceProteins;
    }
}
