package org.reactome.release.downloadDirectory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class GenerateGOAnnotationFile {

    private static final Logger logger = LogManager.getLogger();
    private static final String CELLULAR_COMPONENT = "Cellular Component";
    private static final String MOLECULAR_FUNCTION = "Molecular Function";
    private static final String BIOLOGICAL_PROCESS = "Biological Process";
    private static final List<String> goTerms = new ArrayList<>(Arrays.asList(CELLULAR_COMPONENT, MOLECULAR_FUNCTION, BIOLOGICAL_PROCESS));
    private static final String GOA_FILENAME = "gene_association.reactome";
    private static final int MAX_RECURSION_LEVEL = 2;
    private static final String uniprotDbString = "UniProtKB";
    private static final String PROTEIN_BINDING_ANNOTATION = "0005515";
    private static final List<String> speciesWithAlternateGOCompartment = new ArrayList<>(Arrays.asList("11676", "211044", "1491", "1392"));
    private static final List<String> microbialSpeciesToExclude = new ArrayList<>(Arrays.asList("813", "562", "491", "90371", "1280", "5811"));
    private static Set<String> goaLines = new HashSet<>();
    private static Map<String, Integer> dates = new HashMap<>();

    public static void execute(MySQLAdaptor dbAdaptor, String releaseNumber) throws Exception {

        logger.info("Generating GO annotation file: gene_association.reactome");

        for (GKInstance reactionInst : (Collection<GKInstance>) dbAdaptor.fetchInstancesByClass("ReactionlikeEvent")) {
            // Only finding GO accessions from curated ReactionlikeEvents
            if (!isInferred(reactionInst)) {
                Collection<GKInstance> catalystInstances = reactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
                for (String goTerm : goTerms) {
                    // All proteins in a Reaction are examined for CC accessions. All proteins are examined for BP accessions if the Reaction has no catalyst.
                    // TODO: Rule explanation
                    if (goTerm.equals(CELLULAR_COMPONENT) || (goTerm.equals(BIOLOGICAL_PROCESS) && catalystInstances.size() == 0)) {
                        Set<GKInstance> proteinInstances = getReactionProteins(reactionInst);
                        processProteins(goTerm, proteinInstances, reactionInst, null);
                    } else {
                        // If looking at MF of an instance, or BP of an instance that has catalyst instances.
                        for (GKInstance catalystInst : catalystInstances) {
                            // Check that catalyst instance has no disqualifying attributes.
                            if (validCatalyst(catalystInst, goTerm)) {
                                Set<GKInstance> proteinInstances = new HashSet<>();
                                // For MF annotations, gets all proteins from the ActiveUnit if it exists or from the PhysicalEntity if not.
                                if (goTerm.equals(MOLECULAR_FUNCTION)) {
                                    GKInstance activeUnitInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit);
                                    GKInstance entityInst = activeUnitInst == null ? (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity) : activeUnitInst;
                                    proteinInstances = getMolecularFunctionProteins(entityInst);
                                    // For BP annotations (that have a catalyst), it gets all proteins from the PhysicalEntity
                                } else if (goTerm.equals(BIOLOGICAL_PROCESS)) {
                                    proteinInstances = getBiologicalProcessProteins((GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity));
                                }
                                processProteins(goTerm, proteinInstances, reactionInst, catalystInst);
                            }
                        }
                    }
                }
            }
        }
        outputGOAFile();
        Files.move(Paths.get(GOA_FILENAME), Paths.get(releaseNumber + "/" + GOA_FILENAME), StandardCopyOption.REPLACE_EXISTING);
        logger.info("Finished generating gene_association.reactome");
    }

    // This method uses an AttributeQueryRequest to find all Proteins (above and below in the hierarchy) associated with a ReactionlikeEvent.
    private static Set<GKInstance> getReactionProteins(GKInstance reactionInst) throws Exception {
        List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<>();
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Pathway, new String[]{ReactomeJavaConstants.hasEvent}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.ReactionlikeEvent, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Reaction, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.CatalystActivity, new String[]{ReactomeJavaConstants.physicalEntity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Complex, new String[]{ReactomeJavaConstants.hasComponent}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.EntitySet, new String[]{ReactomeJavaConstants.hasMember}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Polymer, new String[]{ReactomeJavaConstants.repeatedUnit}, new String[]{}));

        // All EWAS' associated with each of the above-stated classes will be output for this ReactionlikeEvent.
        String[] outClasses = new String[]{ReactomeJavaConstants.EntityWithAccessionedSequence};
        return InstanceUtilities.followInstanceAttributes(reactionInst, classesToFollow, outClasses);
    }

    // Retrieves all protein instances from an EntitySet comprised of only EWAS' or that are not a Complex/Polymer but are an EWAS.
    private static Set<GKInstance> getMolecularFunctionProteins(GKInstance entityInst) throws Exception {
        Set<GKInstance> proteinInstances = new HashSet<>();
        SchemaClass entitySchemaClass = entityInst.getSchemClass();
        if (!(entitySchemaClass.isa(ReactomeJavaConstants.Complex) || entitySchemaClass.isa(ReactomeJavaConstants.Polymer))) {
            if (entitySchemaClass.isa(ReactomeJavaConstants.EntitySet) && onlyEWASMembers(entityInst)) {
                proteinInstances.addAll(entityInst.getAttributeValuesList(ReactomeJavaConstants.hasMember));
            } else if (entitySchemaClass.isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
                proteinInstances.add(entityInst);
            }
        }
        return proteinInstances;
    }

    // Returns all protein instances from multi-instance PhysicalEntitys (Polymer/Complex/EntitySet) or just the single incoming instance if it is an EWAS.
    private static Set<GKInstance> getBiologicalProcessProteins(GKInstance physicalEntityInst) throws Exception {
        Set<GKInstance> proteinInstances = new HashSet<>();
        if (multiInstancePhysicalEntity(physicalEntityInst.getSchemClass())) {
            proteinInstances.addAll(getMultiInstanceSubInstances(physicalEntityInst));
        } else if (physicalEntityInst.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
            proteinInstances.add(physicalEntityInst);
        }
        return proteinInstances;
    }

    // Attempt to generate GO Annotation information for each protein associated with the whole Reaction or just it's catalysts, depending on the goTerm being evaluated.
    private static void processProteins(String goTerm, Set<GKInstance> proteinInstances, GKInstance reactionInst, GKInstance catalystInst) throws Exception {

        for (GKInstance proteinInst : proteinInstances) {
            GKInstance referenceEntityInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            GKInstance speciesInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.species);
            // Check if the protein has any disqualifying attributes.
            if (validProtein(referenceEntityInst, speciesInst)) {
                String taxonIdentifier = ((GKInstance) speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
                // TODO: Rule check
                if (!microbialSpeciesToExclude.contains(taxonIdentifier)) {

                    if (goTerm.equals(CELLULAR_COMPONENT)) {
                        // For CC annotations, alternateGOCompartments are disqualifying.
                        //TODO: Rule check
                        if (!speciesWithAlternateGOCompartment.contains(taxonIdentifier)) {
                            // For CC, we are looking at ALL proteins in a ReactionlikeEvent
                            goaLines.add(getGOCellularCompartmentLine(proteinInst, referenceEntityInst, reactionInst, taxonIdentifier));
                        }
                    }

                    // For MF, which are ONLY CatalystActivity instances, the activity attribute must be populated.
                    // Multiple GOA lines can be generated form a single catalyst instance (see getGOMolecularFunctionLine)
                    if (goTerm.equals(MOLECULAR_FUNCTION)) {
                        if (catalystInst.getAttributeValue(ReactomeJavaConstants.activity) != null) {
                            goaLines.addAll(getGOMolecularFunctionLine(catalystInst, referenceEntityInst, taxonIdentifier, reactionInst));
                        }
                    }

                    // For BP, which can describe either the ENTIRE reaction or just its catalysts, retrieving all accessions requires a recursive check of the parents of the reaction.
                    if (goTerm.equals(BIOLOGICAL_PROCESS)) {
                        goaLines.addAll(getGOBiologicalProcessLine(referenceEntityInst, reactionInst, taxonIdentifier));
                    }
                }
            }
        }
    }

    // This feeds into the GOA line generator. It formats that method call with attributes specific to CC annotations.
    private static String getGOCellularCompartmentLine(GKInstance proteinInst, GKInstance referenceEntityInst, GKInstance reactionInst, String taxonIdentifier) throws Exception {
        String reactomeIdentifier = "REACTOME:" + ((GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
        String goCellularCompartmentAccession = getCellularCompartmentGOAccession(proteinInst);
        String goaLine = generateGOALine(referenceEntityInst, "C", goCellularCompartmentAccession, reactomeIdentifier, "TAS", taxonIdentifier);
        assignDateForGOALine(proteinInst, goaLine);
        return goaLine;
    }

    // Checks for and returns valid GO accessions specific to Cellular Compartments in the protein of interest.
    private static String getCellularCompartmentGOAccession(GKInstance proteinInst) throws Exception {
        GKInstance compartmentInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.compartment);
        if (compartmentInst != null && !compartmentInst.getAttributeValue(ReactomeJavaConstants.accession).toString().equals(PROTEIN_BINDING_ANNOTATION)) {
            return "GO:" + compartmentInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
        }
        return null;
    }

    // Generating GOA lines for MF annotations depends on if the catalyst has any literatureReferences (meaning it has a PubMed annotation). If it does, multiple GOA lines
    // that are specific to each PubMed annotation will be output, or, if there are no literatureReferences just a single line with a Reactome identifier will be output.
    // The GOA generation will be called differently depending on this.
    private static List<String> getGOMolecularFunctionLine(GKInstance catalystInst, GKInstance referenceEntityInst, String taxonIdentifier, GKInstance reactionInst) throws Exception {
        List<String> goAnnotationLines = new ArrayList<>();
        List<String> pubMedIdentifiers = new ArrayList<>();
        for (GKInstance literatureReferenceInst : (Collection<GKInstance>) catalystInst.getAttributeValuesList(ReactomeJavaConstants.literatureReference)) {
            pubMedIdentifiers.add("PMID:" + literatureReferenceInst.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier).toString());
        }
        GKInstance activityInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.activity);
        if (!activityInst.getAttributeValue(ReactomeJavaConstants.accession).toString().equals(PROTEIN_BINDING_ANNOTATION)) {
            String goAccession = "GO:" + activityInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
            if (pubMedIdentifiers.size() > 0) {
                for (String pubmedIdentifier : pubMedIdentifiers) {
                    String goaLine = generateGOALine(referenceEntityInst, "F", goAccession, pubmedIdentifier, "EXP", taxonIdentifier);
                    assignDateForGOALine(catalystInst, goaLine);
                    goAnnotationLines.add(goaLine);
                }
            } else {
                String reactomeIdentifier=  "REACTOME:" + ((GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
                String goaLine = generateGOALine(referenceEntityInst, "F", goAccession, reactomeIdentifier, "TAS", taxonIdentifier);
                assignDateForGOALine(catalystInst, goaLine);
                goAnnotationLines.add(goaLine);
            }
        }
        return goAnnotationLines;
    }

    // Before creating GOA line for BP annotations, the reaction in question needs to be checked for the existance of a 'goBiologicalProcess' attribute. If there is none
    // than the instance's 'hasEvent' referrals are checked for any.
    private static List<String> getGOBiologicalProcessLine(GKInstance referenceEntityInst, GKInstance reactionInst, String taxonIdentifier) throws Exception {
        List<String> goAnnotationLines = new ArrayList<>();
        for (Map<String, String> biologicalProcessAccession : getGOBiologicalProcessAccessions(reactionInst, 0)) {
            String goaLine = generateGOALine(referenceEntityInst, "P", biologicalProcessAccession.get("accession"), biologicalProcessAccession.get("event"), "TAS", taxonIdentifier);
            assignDateForGOALine(reactionInst, goaLine);
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

    // Generic function that generates a single line in the GOA file. The arguments are formatted in GO term-specific methods before this is called.
    private static String generateGOALine(GKInstance referenceEntityInst, String goLetter, String goAccession, String eventIdentifier, String evidenceCode, String taxonIdentifier) throws Exception {
        List<String> goaLine = new ArrayList<>();
        goaLine.add(uniprotDbString);
        goaLine.add(referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
        goaLine.add(getSecondaryIdentifier(referenceEntityInst));
        goaLine.add("");
        goaLine.add(goAccession);
        goaLine.add(eventIdentifier);
        goaLine.add(evidenceCode);
        goaLine.add("");
        goaLine.add(goLetter);
        goaLine.add("");
        goaLine.add("");
        goaLine.add("protein");
        goaLine.add("taxon:" + taxonIdentifier);
        return String.join("\t", goaLine);
    }

    // This method checks a catalyst instances validity: First, it checks that the catalyst instance's PhysicalEntity has a Compartment.
    // For MF annotations, it also checks the contents of the ActiveUnit attribute in a few ways:
    // If there are no active unit instances, it cannot be a multi-instance PE (Complex/Polymer/EntitySet).
    // If there is only 1 active unit, it cannot be a multi-instance PE unless it is an EntitySet that is only comprised of EWAS'.
    // If there is more than 1 active unit, it is disqualified from the GOA file.
    private static boolean validCatalyst(GKInstance catalystInst, String goTerm) throws Exception {
        GKInstance physicalEntityInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity);
        if (physicalEntityInst != null && physicalEntityInst.getAttributeValue(ReactomeJavaConstants.compartment) != null) {
            if (goTerm.equals(MOLECULAR_FUNCTION)) {
                List<GKInstance> activeUnitInstances = catalystInst.getAttributeValuesList(ReactomeJavaConstants.activeUnit);
                if (activeUnitInstances.size() == 0 && !multiInstancePhysicalEntity(physicalEntityInst.getSchemClass())) {
                    return true;
                } else if (activeUnitInstances.size() == 1) {
                    SchemaClass activeUnitSchemaClass = (activeUnitInstances).get(0).getSchemClass();
                    if (activeUnitSchemaClass.isa(ReactomeJavaConstants.Complex) || activeUnitSchemaClass.isa(ReactomeJavaConstants.Polymer)) {
                        return false;
                    }
                    if (activeUnitSchemaClass.isa(ReactomeJavaConstants.EntitySet) && !onlyEWASMembers((activeUnitInstances).get(0))) {
                        return false;
                    }
                } else {
                    return false;
                }
                return true;
            } else {
                return true;
            }
        }
        return false;
    }

    // This checks if the protein in question has UniProt as a Reference Database, and if the species has a crossReference. If not, it's information will not be generated for the GOA file.
    // TODO: Rule check
    private static boolean validProtein(GKInstance referenceEntityInst, GKInstance speciesInst) throws Exception {
        if (referenceEntityInst != null && speciesInst != null) {
            GKInstance referenceDatabaseInst = (GKInstance) referenceEntityInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
            if (referenceDatabaseInst != null && referenceDatabaseInst.getDisplayName().equals("UniProt") && speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference) != null) {
                return true;
            }
        }
        return false;
    }

    // Finds most recent modification date for a GOA line. This is a bit of a moving target since GOA lines can be generated convergently for
    // each type of GO annotation. Depending on if it is looking at the individual protein or whole reaction level, the date attribute
    // may not be the most recent. If it is found that the goaLine was generated earlier but that a more recent modification date exists based on the
    // entity that is currently being checked, then it will just update that date value in the hash associated with the line. (Yes, this is weird).
    private static Integer assignDateForGOALine(GKInstance entityInst, String goaLine) throws Exception {
        int instanceDate;
        Collection<GKInstance> modifiedInstances = entityInst.getAttributeValuesList(ReactomeJavaConstants.modified);
        if (modifiedInstances.size() > 0) {
            List<GKInstance> modifiedInstancesList = new ArrayList<>(modifiedInstances);
            GKInstance mostRecentModifiedInst = modifiedInstancesList.get(modifiedInstancesList.size() - 1);
            instanceDate = Integer.valueOf(mostRecentModifiedInst.getAttributeValue(ReactomeJavaConstants.dateTime).toString().split(" ")[0].replaceAll("-", ""));
        } else {
            GKInstance createdInst = (GKInstance) entityInst.getAttributeValue(ReactomeJavaConstants.created);
            instanceDate = Integer.valueOf(createdInst.getAttributeValue(ReactomeJavaConstants.dateTime).toString().split(" ")[0].replaceAll("-", ""));
        }

        // Stores date in global hash that allows date value to be updated if a more recent date was found.
        if (dates.get(goaLine) == null) {
            dates.put(goaLine, instanceDate);
        } else {
            if (instanceDate > dates.get(goaLine)) {
                dates.put(goaLine, instanceDate);
            }
        }
        return instanceDate;
    }

    // Generates the third column of the GOA line by checking attributes of the referenceEntity instance.
    private static String getSecondaryIdentifier(GKInstance referenceEntityInst) throws Exception {
        if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier).toString();
        } else if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName).toString();
        } else {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
        }
    }

    // Checks if this PhysicalEntity is one that is made of multiple PhysicalEntity instances.
    private static boolean multiInstancePhysicalEntity(SchemaClass physicalEntitySchemaClass) {
        if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.Complex) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.EntitySet) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.Polymer)) {
            return true;
        }
        return false;
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

    // Checks that the incoming EntitySet instance only has EWAS members
    private static boolean onlyEWASMembers(GKInstance entitySetInst) throws Exception {
        Collection<GKInstance> memberInstances = entitySetInst.getAttributeValuesList(ReactomeJavaConstants.hasMember);
        if (memberInstances.size() > 0) {
            for (GKInstance memberInst : memberInstances) {
                if (!memberInst.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    // Parent method that houses electronically and manually inferred instance checks.
    private static boolean isInferred(GKInstance reactionInst) throws Exception {
        if (isElectronicallyInferred(reactionInst) || isManuallyInferred(reactionInst)) {
            return true;
        }
        return false;
    }

    private static boolean isManuallyInferred(GKInstance reactionInst) throws Exception {
        return reactionInst.getAttributeValue(ReactomeJavaConstants.inferredFrom) != null ? true : false;
    }

    private static boolean isElectronicallyInferred(GKInstance reactionInst) throws Exception {
        return reactionInst.getAttributeValue(ReactomeJavaConstants.evidenceType) != null ? true : false;
    }

    // With all GOA annotations made and most recent dates for each line found, generate the GOA file.
    private static void outputGOAFile() throws IOException {
        if (Files.exists(Paths.get(GOA_FILENAME))) {
            Files.delete(Paths.get(GOA_FILENAME));
        }
        List<String> sortedGoaLines = new ArrayList<>(goaLines);
        Collections.sort(sortedGoaLines);
        BufferedWriter br = new BufferedWriter((new FileWriter(GOA_FILENAME)));
        br.write("!gaf-version: 2.1\n");
        for (String goaLine : sortedGoaLines) {
            br.append(goaLine + "\t" + dates.get(goaLine) + "\tReactome\t\t\n");
        }
        br.close();
    }
}
