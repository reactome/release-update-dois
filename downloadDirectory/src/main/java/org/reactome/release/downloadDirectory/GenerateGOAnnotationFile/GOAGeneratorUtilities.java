package org.reactome.release.downloadDirectory.GenerateGOAnnotationFile;

import org.gk.model.ClassAttributeFollowingInstruction;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.SchemaClass;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class GOAGeneratorUtilities {

    private static final List<String> microbialSpeciesToExclude = new ArrayList<>(Arrays.asList("813", "562", "491", "90371", "1280", "5811"));
    private static final String PROTEIN_BINDING_ANNOTATION = "0005515";
    private static final String uniprotDbString = "UniProtKB";
    private static Map<String, Integer> dates = new HashMap<>();
    private static Set<String> goaLines = new HashSet<>();

    public static Set<GKInstance> retrieveProteins(GKInstance reactionInst) throws Exception {
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

    // This checks if the protein in question has UniProt as a Reference Database, and if the species has a crossReference. If not, it's information will not be generated for the GOA file.
    public static boolean validateProtein(GKInstance referenceEntityInst, GKInstance speciesInst) throws Exception {
        if (referenceEntityInst != null && speciesInst != null) {
            GKInstance referenceDatabaseInst = (GKInstance) referenceEntityInst.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
            if (referenceDatabaseInst != null && referenceDatabaseInst.getDisplayName().equals("UniProt") && speciesInst.getAttributeValue(ReactomeJavaConstants.crossReference) != null) {
                return true;
            }
        }
        return false;
    }

    // This method checks the validity of the PhysicalEntity of a Catalyst instance by checking it has a compartment attribute.
    public static boolean validateCatalystPE(GKInstance catalystPEInst) throws Exception {
        return catalystPEInst != null && catalystPEInst.getAttributeValue(ReactomeJavaConstants.compartment) != null;
    }

    // Checks if this PhysicalEntity is one that is made of multiple PhysicalEntity instances.
    public static boolean multiInstancePhysicalEntity(SchemaClass physicalEntitySchemaClass) {
        return physicalEntitySchemaClass.isa(ReactomeJavaConstants.Complex) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.EntitySet) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.Polymer);
    }

    // Generic function that generates a single line in the GOA file. The arguments are formatted in GO term-specific methods before this is called.
    public static String generateGOALine(GKInstance referenceEntityInst, String goLetter, String goAccession, String eventIdentifier, String evidenceCode, String taxonIdentifier) throws Exception {
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
        goaLines.add(String.join("\t", goaLine));
        return String.join("\t", goaLine);
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

    // Finds most recent modification date for a GOA line. This is a bit of a moving target since GOA lines can be generated convergently for
    // each type of GO annotation. Depending on if it is looking at the individual protein or whole reaction level, the date attribute
    // may not be the most recent. If it is found that the goaLine was generated earlier but that a more recent modification date exists based on the
    // entity that is currently being checked, then it will just update that date value in the hash associated with the line. (Yes, this is weird).
    public static Integer assignDateForGOALine(GKInstance entityInst, String goaLine) throws Exception {
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

    // With all GOA annotations made and most recent dates for each line found, generate the GOA file.
    public static void outputGOAFile(String filename) throws IOException {
        if (Files.exists(Paths.get(filename))) {
            Files.delete(Paths.get(filename));
        }
        List<String> sortedGoaLines = new ArrayList<>(goaLines);
        Collections.sort(sortedGoaLines);
        BufferedWriter br = new BufferedWriter((new FileWriter(filename)));
        br.write("!gaf-version: 2.1\n");
        for (String goaLine : sortedGoaLines) {
            br.append(goaLine + "\t" + dates.get(goaLine) + "\tReactome\t\t\n");
        }
        br.close();
    }

    public static boolean excludedMicrobialSpecies(String taxonIdentifier) {
        return microbialSpeciesToExclude.contains(taxonIdentifier);
    }

    public static boolean accessionForProteinBindingAnnotation(Object goAccession) {
        return goAccession.toString().equals(PROTEIN_BINDING_ANNOTATION);
    }
}
