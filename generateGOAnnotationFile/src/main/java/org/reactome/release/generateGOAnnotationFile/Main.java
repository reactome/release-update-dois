package org.reactome.release.generateGOAnnotationFile;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaClass;

import java.io.FileInputStream;
import java.util.*;

public class Main {

    private static final String uniprotDbString = "UniProtKB";
    private static final String PROTEIN_BINDING_ANNOTATION = "0005515";
    private static final List<String> speciesWithAlternateGOCompartment = new ArrayList<>(Arrays.asList("11676", "211044", "1491", "1392"));
    private static final List<String> microbialSpeciesToExclude = new ArrayList<>(Arrays.asList("813", "562", "491", "90371", "1280", "5811"));
    private static List<String> goCategories = new ArrayList<>(Arrays.asList("C", "M", "B"));
    private static Set<String> goaLines = new HashSet<>();

    public static void main(String[] args) throws Exception {

        Properties props = new Properties();
        props.load(new FileInputStream("src/main/resources/config.properties"));

        String username = props.getProperty("username");
        String password = props.getProperty("password");
        String database = props.getProperty("database");
        String host = props.getProperty("host");
        int port = Integer.valueOf(props.getProperty("port"));

        MySQLAdaptor dbAdaptor = new MySQLAdaptor(host, database, username, password, port);

        for (GKInstance reactionInst : (Collection<GKInstance>) dbAdaptor.fetchInstancesByClass("ReactionlikeEvent")) {
            
            if (!isInferred(reactionInst)) {
                for (String goLetter : goCategories) {

                    Collection<GKInstance> catalystInstances = reactionInst.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);

                    if (goLetter.equals("C") || (goLetter.equals("B") && catalystInstances.size() == 0)) {
                        Set<GKInstance> proteins = findProteins(reactionInst);
                        processProteins(goLetter, proteins, reactionInst, null);
                    } else {
                        for (GKInstance catalystInst : catalystInstances) {
                            if (validCatalyst(catalystInst, goLetter)) {

                                if (goLetter.equals("M")) {
                                    GKInstance activeUnitInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.activeUnit);
                                    GKInstance entityInst = activeUnitInst != null ? activeUnitInst : (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity);
                                    Set<GKInstance> proteins = getMolecularFunctionProteins(entityInst);
                                    processProteins(goLetter, proteins, reactionInst, catalystInst);
                                } else {
                                    //TODO
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static Set<GKInstance> findProteins(GKInstance reactionInst) throws Exception {
        List<ClassAttributeFollowingInstruction> classesToFollow = new ArrayList<>();
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Pathway, new String[]{ReactomeJavaConstants.hasEvent}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.ReactionlikeEvent, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Reaction, new String[]{ReactomeJavaConstants.input, ReactomeJavaConstants.output, ReactomeJavaConstants.catalystActivity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.CatalystActivity, new String[]{ReactomeJavaConstants.physicalEntity}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Complex, new String[]{ReactomeJavaConstants.hasComponent}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.EntitySet, new String[]{ReactomeJavaConstants.hasMember}, new String[]{}));
        classesToFollow.add(new ClassAttributeFollowingInstruction(ReactomeJavaConstants.Polymer, new String[]{ReactomeJavaConstants.repeatedUnit}, new String[]{}));

        String[] outClasses = new String[]{ReactomeJavaConstants.EntityWithAccessionedSequence};
        return InstanceUtilities.followInstanceAttributes(reactionInst, classesToFollow, outClasses);

    }

    private static Set<GKInstance> getMolecularFunctionProteins(GKInstance entityInst) throws Exception {
        Set<GKInstance> proteins = new HashSet<>();
        if (!(entityInst.getSchemClass().isa(ReactomeJavaConstants.Complex) || entityInst.getSchemClass().isa(ReactomeJavaConstants.Polymer))) {
            if (entityInst.getSchemClass().isa(ReactomeJavaConstants.EntitySet) && onlyEWASMembers(entityInst)) {
                proteins.addAll(entityInst.getAttributeValuesList(ReactomeJavaConstants.hasMember));
            } else if (entityInst.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
                proteins.add(entityInst);
            }
        }
        return proteins;
    }

    private static boolean validCatalyst(GKInstance catalystInst, String goLetter) throws Exception {

        GKInstance physicalEntityInst = (GKInstance) catalystInst.getAttributeValue(ReactomeJavaConstants.physicalEntity);
        if (physicalEntityInst != null && physicalEntityInst.getAttributeValue(ReactomeJavaConstants.compartment) != null) {
            if (goLetter.equals("M")) {
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

    private static boolean onlyEWASMembers(GKInstance activeUnitInst) throws Exception {
        Collection<GKInstance> memberInstances = activeUnitInst.getAttributeValuesList(ReactomeJavaConstants.hasMember);
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

    private static boolean multiInstancePhysicalEntity(SchemaClass physicalEntitySchemaClass) {
        if (physicalEntitySchemaClass.isa(ReactomeJavaConstants.Complex) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.EntitySet) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.Polymer)) {
            return true;
        }
        return false;
    }

    private static void processProteins(String goLetter, Set<GKInstance> proteinInstances, GKInstance reactionInst, GKInstance catalystInst) throws Exception {

        for (GKInstance proteinInst : proteinInstances) {
            GKInstance referenceEntityInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.referenceEntity);
            GKInstance speciesInst = (GKInstance) proteinInst.getAttributeValue(ReactomeJavaConstants.species);
            if (validPhysicalEntity(referenceEntityInst, speciesInst)) {
                if (goLetter.equals("C")) {
                    String goAnnotationLine = getGOCellularCompartmentLine(proteinInst, speciesInst, referenceEntityInst, reactionInst);
                    goaLines.add(goAnnotationLine);
                }
                if (goLetter.equals("M")) {
                }
            }
        }
    }

    private static String getGOCellularCompartmentLine(GKInstance proteinInst, GKInstance speciesInst, GKInstance referenceEntityInst, GKInstance reactionInst) throws Exception {
        List<String> goAnnotationLine = new ArrayList<>();
        String taxonIdentifier = ((GKInstance) speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference)).getAttributeValue(ReactomeJavaConstants.identifier).toString();
        if (taxonIdentifier != PROTEIN_BINDING_ANNOTATION && !speciesWithAlternateGOCompartment.contains(taxonIdentifier) && !microbialSpeciesToExclude.contains(taxonIdentifier)) {
            //TODO: GO_CellularComponent check... is it needed?
            //TODO: Compartment check... is it needed?
            GKInstance reactionStableIdentifierInst = (GKInstance) reactionInst.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            goAnnotationLine.add(uniprotDbString);
            goAnnotationLine.add(referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
            goAnnotationLine.add(getSecondaryIdentifier(referenceEntityInst));
            goAnnotationLine.add("");
            goAnnotationLine.add(getGOAccession(proteinInst));
            goAnnotationLine.add("REACTOME:" + reactionStableIdentifierInst.getAttributeValue(ReactomeJavaConstants.identifier).toString());
            goAnnotationLine.add("TAS");
            goAnnotationLine.add("");
            goAnnotationLine.add("C");
            goAnnotationLine.add("");
            goAnnotationLine.add("");
            goAnnotationLine.add("protein");
            goAnnotationLine.add("taxon:" + taxonIdentifier);
        }
        return String.join("\t", goAnnotationLine);
    }

    private static String getGOAccession(GKInstance ewasInst) throws Exception {
        GKInstance compartmentInst = (GKInstance) ewasInst.getAttributeValue(ReactomeJavaConstants.compartment);
        if (compartmentInst != null) {
            return "GO:" + compartmentInst.getAttributeValue(ReactomeJavaConstants.accession).toString();
        }
        return null;
    }

    private static String getSecondaryIdentifier(GKInstance referenceEntityInst) throws Exception {
        if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier).toString();
        } else if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName).toString();
        } else {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
        }
    }

    private static boolean validPhysicalEntity(GKInstance referenceEntityInst, GKInstance speciesInst) throws Exception {
        if (referenceEntityInst != null && speciesInst != null) {
            GKInstance referenceDatabaseInst = (GKInstance) referenceEntityInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
            if (referenceDatabaseInst != null && referenceDatabaseInst.getDisplayName().equals("UniProt") && speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference) != null) {
                return true;
            }
        }
        return false;
    }

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
}
