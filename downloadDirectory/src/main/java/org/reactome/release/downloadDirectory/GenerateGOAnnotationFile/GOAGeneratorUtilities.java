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
import java.nio.file.StandardCopyOption;
import java.util.*;

public class GOAGeneratorUtilities {

    // CrossReference IDs of excluded microbial species: C. trachomatis, E. coli, N. meningitidis, S. typhimurium, S. aureus, and T. gondii
    private static final List<String> microbialSpeciesToExclude = new ArrayList<>(Arrays.asList("813", "562", "491", "90371", "1280", "5811"));
    // This GO accession pertains to protein binding, which would require an "IPI" prefix. Excluded for now.
    private static final String PROTEIN_BINDING_ANNOTATION = "0005515";
    private static final String uniprotDbString = "UniProtKB";
    private static Map<String, Integer> dates = new HashMap<>();
    private static Set<String> goaLines = new HashSet<>();

    /**
     * Performs an AttributeQueryRequest on the incoming reaction instance. This will retrieve all protein's affiliated with the Reaction.
     * @param reactionInst
     * @return -- Set of GKInstances output from the AttributeQueryRequest
     * @throws Exception
     */
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

    /**
     * Verifys existence of ReferenceEntity and Species, and that the ReferenceDatabase associated with the ReferenceEntity is from UniProt.
     * @param referenceEntityInst -- ReferenceEntity instance from the protein/catalyst/reaction.
     * @param speciesInst -- Species instance from the protein/catalyst/reaction.
     * @return -- true/false indicating protein validity.
     * @throws Exception
     */
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

    /**
     * Shared catalyst validation method between MolecularFunction and BiologicalProcess classes that checks for existence of compartment attribute.
     * @param catalystPEInst -- PhysicalEntity attribute from a Catalyst instance
     * @return -- true/false indicating PhysicalEntity validity.
     * @throws Exception
     */
    public static boolean validateCatalystPE(GKInstance catalystPEInst) throws Exception {
        return catalystPEInst != null && catalystPEInst.getAttributeValue(ReactomeJavaConstants.compartment) != null;
    }

    /**
     * Checks if the PhysicalEntity is a 'multi-instance' type, which includes Complexes, EntitySets, and Polymers
     * @param physicalEntitySchemaClass
     * @return
     */
    public static boolean multiInstancePhysicalEntity(SchemaClass physicalEntitySchemaClass) {
        return physicalEntitySchemaClass.isa(ReactomeJavaConstants.Complex) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.EntitySet) || physicalEntitySchemaClass.isa(ReactomeJavaConstants.Polymer);
    }

    /**
     * Builds most of the GO annotation line that will be added to gene_association.reactome.
     * @param referenceEntityInst -- ReferenceEntity instance from the protein/catalyst/reaction.
     * @param goLetter -- Can be "C", "F" or "P" for Cellular Component, Molecular Function, or Biological Process annotations, respectively.
     * @param goAccession -- GO accession taken from the protein/catalyst/reaction instance.
     * @param eventIdentifier -- StableIdentifier of the protein/catalyst/reaction. Will have either a 'REACTOME' or 'PMID' prefix.
     * @param evidenceCode -- Will be either "TAS" (Traceable Author Statement) or "EXP" (Experimentally Inferred). Most will be TAS, unless there is a PMID accession.
     * @param taxonIdentifier -- Reactome Species CrossReference identifier.
     * @return -- GO annotation line, excluding the DateTime and 'Reactome' columns.
     * @throws Exception
     */
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

    /**
     * Returns the value for the 'secondaryIdentifier' column in the GOA line.
     * @param referenceEntityInst -- ReferenceEntity instance from the protein/catalyst/reaction.
     * @return
     * @throws Exception
     */
    private static String getSecondaryIdentifier(GKInstance referenceEntityInst) throws Exception {
        if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.secondaryIdentifier).toString();
        } else if (referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName) != null) {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.geneName).toString();
        } else {
            return referenceEntityInst.getAttributeValue(ReactomeJavaConstants.identifier).toString();
        }
    }

    /**
     * Checks that the protein is not from an excluded microbial taxon.
     * @param taxonIdentifier -- Protein's Species' CrossReference identifier.
     * @return
     */
    public static boolean excludedMicrobialSpecies(String taxonIdentifier) {
        return microbialSpeciesToExclude.contains(taxonIdentifier);
    }

    /**
     * Checks that the GO accession is not for Protein Binding. These don't receive a GO annotation since they require an "IPI" evidence code.
     * @param goAccession -- GO accession string.
     * @return
     */
    public static boolean proteinBindingAnnotation(Object goAccession) {
        return goAccession.toString().equals(PROTEIN_BINDING_ANNOTATION);
    }

    /**
     * Finds most recent modification date for a GOA line. This is a bit of a moving target since GOA lines can be generated convergently for
     * each type of GO annotation. Depending on if it is looking at the individual protein or whole reaction level, the date attribute
     * may not be the most recent. If it is found that the goaLine was generated earlier but that a more recent modification date exists based on the
     * entity that is currently being checked, then it will just update that date value in the hash associated with the line. (Yes, this is weird).
     * @param entityInst -- Protein/catalyst/reaction that is receiving a GO annotation.
     * @param goaLine -- GO annotation line, used for checking the 'dates' structure.
     * @return
     * @throws Exception
     */
    public static Integer assignDateForGOALine(GKInstance entityInst, String goaLine) throws Exception {
        int instanceDate;
        Collection<GKInstance> modifiedInstances = entityInst.getAttributeValuesList(ReactomeJavaConstants.modified);
        if (modifiedInstances.size() > 0) {
            List<GKInstance> modifiedInstancesList = new ArrayList<>(modifiedInstances);
            GKInstance mostRecentModifiedInst = modifiedInstancesList.get(modifiedInstancesList.size() - 1);
            instanceDate = getDate(mostRecentModifiedInst);
        } else {
            GKInstance createdInst = (GKInstance) entityInst.getAttributeValue(ReactomeJavaConstants.created);
            instanceDate = getDate(createdInst);
        }

        // Stores date in global hash that allows date value to be updated if a more recent date was found.
        if (dates.get(goaLine) == null || instanceDate > dates.get(goaLine)) {
            dates.put(goaLine, instanceDate);
        }
        return instanceDate;
    }

    /**
     * Retrieves date from instance and formats it for GO annotation file.
     * @param instanceEditInst
     * @return
     * @throws Exception
     */
    private static int getDate(GKInstance instanceEditInst) throws Exception {
        return Integer.valueOf(instanceEditInst.getAttributeValue(ReactomeJavaConstants.dateTime).toString().split(" ")[0].replaceAll("-", ""));
    }

    /**
     * Iterates through the lines in the 'goaLines' list, retrieves the date associated with that line and also adds the 'Reactome' column before adding it to the gene_association.reactome file.
     * @param filename
     * @throws IOException
     */
    public static void outputGOAFile(String filename) throws IOException {

        Files.deleteIfExists(Paths.get(filename));
        List<String> sortedGoaLines = new ArrayList<>(goaLines);
        Collections.sort(sortedGoaLines);
        BufferedWriter br = new BufferedWriter((new FileWriter(filename)));
        br.write("!gaf-version: 2.1\n");
        for (String goaLine : sortedGoaLines) {
            br.append(goaLine + "\t" + dates.get(goaLine) + "\tReactome\t\t\n");
        }
        br.close();
    }

    // Move file into DownloadDirectory folder corresponding to release number.
    public static void moveFile(String filename, String targetDirectory) throws IOException {
        Files.move(Paths.get(filename), Paths.get(targetDirectory), StandardCopyOption.REPLACE_EXISTING);
    }
}
